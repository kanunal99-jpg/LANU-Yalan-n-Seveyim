package com.lanu.yalan.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.lanu.yalan.ai.FreeAiProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Personal provider key stays on this device, outside progress, portable backups and logs. */
@Singleton
class OnlineAiCredentials @Inject constructor(@ApplicationContext context: Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"online-ai.enc"))
    private val mutex=Mutex()
    private val alias="lanu.online.ai"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    suspend fun read(): String?=withContext(Dispatchers.IO) {mutex.withLock {
        val bytes=try {file.openRead().use {input ->
            val buffer=ByteArray(1025);var size=0
            while(size<buffer.size) {val n=input.read(buffer,size,buffer.size-size);if(n<0) break;size+=n}
            require(size<=1024);buffer.copyOf(size)
        }} catch(e: FileNotFoundException) {
            if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return@withLock null else throw e
        }
        require(bytes.size in 29..1024)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        val plain=cipher.doFinal(bytes.copyOfRange(12,bytes.size)).decodeToString(throwOnInvalidSequence=true)
        val prefix="${FreeAiProtocol.CONSENT_VERSION}:"
        require(plain.startsWith(prefix))
        plain.removePrefix(prefix).also {require(FreeAiProtocol.validKey(it))}
    }}
    suspend fun write(value: String)=withContext(Dispatchers.IO) {mutex.withLock {
        require(FreeAiProtocol.validKey(value))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val bytes=cipher.iv+cipher.doFinal("${FreeAiProtocol.CONSENT_VERSION}:$value".encodeToByteArray())
        val stream=file.startWrite()
        try {stream.write(bytes);file.finishWrite(stream)} catch(e: Exception) {file.failWrite(stream);throw e}
    }}
    suspend fun delete()=withContext(Dispatchers.IO) {mutex.withLock {file.delete()}}
}
