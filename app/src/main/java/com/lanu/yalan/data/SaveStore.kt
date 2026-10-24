package com.lanu.yalan.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.lanu.yalan.domain.GameSave
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.filesDir, "progress.enc"))
    // AtomicFile readers must share the writer's lock, even across fresh repository instances.
    companion object { private val mutex=Mutex() }
    private val json = Json { ignoreUnknownKeys = true }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("lanu.progress", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("lanu.progress", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    suspend fun read(): GameSave = withContext(Dispatchers.IO) { mutex.withLock { readStored() } }
    private fun readStored(): GameSave {
        // Let AtomicFile recover its backup before deciding this is a new install.
        val bytes = try { file.openRead().use { PortableBackup.readBounded(it) } } catch (e: FileNotFoundException) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
                return GameSave()
            }
            throw e
        }
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val save = json.decodeFromString<GameSave>(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString())
        return SaveValidation.validate(save)
    }
    suspend fun write(save: GameSave) = withContext(Dispatchers.IO) { mutex.withLock { writeStored(save) } }
    private fun writeStored(save: GameSave) {
        SaveValidation.validate(save)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plain=json.encodeToString(save).encodeToByteArray()
        val bytes=try {
            require(plain.size<=PortableBackup.MAX_PAYLOAD_BYTES) { "Kayıt boyutu sınırı aşıldı" }
            cipher.iv+cipher.doFinal(plain)
        } finally {plain.fill(0)}
        val stream = file.startWrite()
        try {
            stream.write(bytes);file.finishWrite(stream)
            // AtomicFile reports some sync/rename failures only in system logs. Do not acknowledge them.
            val committed=file.openRead().use {PortableBackup.readBounded(it)}
            check(committed.contentEquals(bytes)) { "Kayıt yazımı doğrulanamadı" }
        }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    suspend fun exportBackup(passphrase: CharArray): ByteArray = withContext(Dispatchers.IO) {
        mutex.withLock { PortableBackup.encode(readStored(),passphrase) }
    }
    suspend fun restoreBackup(bytes: ByteArray, passphrase: CharArray): GameSave = withContext(Dispatchers.IO) {
        // This does not read the old file: a valid backup can recover an unreadable device save.
        val restored=PortableBackup.decode(bytes,passphrase)
        currentCoroutineContext().ensureActive()
        mutex.withLock { writeStored(restored) }
        restored
    }
    suspend fun reset() = withContext(Dispatchers.IO) { mutex.withLock { file.delete() } }
}
