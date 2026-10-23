package com.lanu.yalan.data

import com.lanu.yalan.domain.GameSave
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class BackupFailure { PASSWORD, FORMAT, AUTHENTICATION, CONTENT }
class BackupException(val reason: BackupFailure, message: String=when(reason) {
    BackupFailure.PASSWORD -> "Yedek parolası 12–128 karakter olmalı."
    BackupFailure.FORMAT -> "Yedek dosyası geçersiz veya desteklenmiyor."
    BackupFailure.AUTHENTICATION -> "Yedek açılamadı. Parola yanlış veya dosya değiştirilmiş/bozuk."
    BackupFailure.CONTENT -> "Yedek içeriği geçersiz; mevcut ilerlemen değiştirilmedi."
}, cause: Throwable? = null) : IllegalArgumentException(message,cause)

/** Portable password encryption: the Android Keystore key is deliberately never exported. */
object PortableBackup {
    const val MIN_PASSPHRASE_CHARS=12
    const val MAX_PASSPHRASE_CHARS=128
    const val MAX_FILE_BYTES=256*1024
    const val MAX_PAYLOAD_BYTES=128*1024
    const val KDF_ITERATIONS=600_000
    private const val SALT_BYTES=16
    private const val IV_BYTES=12
    private const val TAG_BYTES=16
    private val magic="LANUBAK1".encodeToByteArray()
    private val headerBytes=magic.size+4+SALT_BYTES+IV_BYTES
    private val random=SecureRandom()
    private val json=Json { ignoreUnknownKeys=true }

    fun encode(save: GameSave, passphrase: CharArray): ByteArray {
        validatePassword(passphrase)
        val plain=try { json.encodeToString(SaveValidation.validate(save)).encodeToByteArray() }
        catch(e: IllegalArgumentException) { throw BackupException(BackupFailure.CONTENT,"Kayıt yedeklenemedi; içeriği geçersiz.",e) }
        try {
            if(plain.size>MAX_PAYLOAD_BYTES) throw BackupException(BackupFailure.CONTENT,"Kayıt, yedek boyutu sınırını aşıyor.")
            val salt=ByteArray(SALT_BYTES).also(random::nextBytes)
            val iv=ByteArray(IV_BYTES).also(random::nextBytes)
            val header=ByteBuffer.allocate(headerBytes).order(ByteOrder.BIG_ENDIAN)
                .put(magic).putInt(KDF_ITERATIONS).put(salt).put(iv).array()
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,derive(passphrase,salt),GCMParameterSpec(TAG_BYTES*8,iv))
            cipher.updateAAD(header)
            return header+cipher.doFinal(plain)
        } finally { plain.fill(0) }
    }

    fun decode(bytes: ByteArray, passphrase: CharArray): GameSave {
        validatePassword(passphrase)
        if(bytes.size !in (headerBytes+TAG_BYTES+2)..minOf(MAX_FILE_BYTES,headerBytes+TAG_BYTES+MAX_PAYLOAD_BYTES))
            throw BackupException(BackupFailure.FORMAT,"Yedek dosyasının boyutu geçersiz.")
        val header=bytes.copyOfRange(0,headerBytes)
        val input=ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val marker=ByteArray(magic.size).also { input.get(it) }
        if(!marker.contentEquals(magic) || input.int!=KDF_ITERATIONS)
            throw BackupException(BackupFailure.FORMAT,"Bu dosya desteklenen bir LANU yedeği değil.")
        val salt=ByteArray(SALT_BYTES).also { input.get(it) }
        val iv=ByteArray(IV_BYTES).also { input.get(it) }
        val plain=try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,derive(passphrase,salt),GCMParameterSpec(TAG_BYTES*8,iv))
            cipher.updateAAD(header)
            cipher.doFinal(bytes,headerBytes,bytes.size-headerBytes)
        } catch(e: GeneralSecurityException) {
            throw BackupException(BackupFailure.AUTHENTICATION,"Yedek açılamadı. Parola yanlış veya dosya değiştirilmiş/bozuk.",e)
        }
        try {
            val text=plain.decodeToString(throwOnInvalidSequence=true)
            checkJsonDepth(text)
            return SaveValidation.validate(json.decodeFromString<GameSave>(text))
        } catch(e: IllegalArgumentException) {
            throw BackupException(BackupFailure.CONTENT,"Yedek içeriği geçersiz; mevcut ilerlemen değiştirilmedi.",e)
        } finally { plain.fill(0) }
    }

    /** Bounded before allocation/decryption; suitable for a Storage Access Framework stream. */
    fun readBounded(input: InputStream, maxBytes: Int=MAX_FILE_BYTES): ByteArray {
        require(maxBytes in 1..MAX_FILE_BYTES)
        val output=java.io.ByteArrayOutputStream()
        val buffer=ByteArray(4096)
        while(true) {
            val count=input.read(buffer,0,minOf(buffer.size,maxBytes-output.size()+1))
            if(count<0) return output.toByteArray()
            if(count==0) {
                val one=input.read()
                if(one<0) return output.toByteArray()
                output.write(one)
            } else output.write(buffer,0,count)
            if(output.size()>maxBytes) throw BackupException(BackupFailure.FORMAT,"Yedek dosyası izin verilen boyutu aşıyor.")
        }
    }

    private fun validatePassword(passphrase: CharArray) {
        if(passphrase.size !in MIN_PASSPHRASE_CHARS..MAX_PASSPHRASE_CHARS)
            throw BackupException(BackupFailure.PASSWORD,"Yedek parolası 12–128 karakter olmalı.")
    }
    private fun derive(passphrase: CharArray,salt: ByteArray): SecretKeySpec {
        val spec=PBEKeySpec(passphrase,salt,KDF_ITERATIONS,256)
        var bytes: ByteArray?=null
        return try {
            val material=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            bytes=material
            SecretKeySpec(material,"AES")
        } finally { spec.clearPassword();bytes?.fill(0) }
    }
    private fun checkJsonDepth(text: String) {
        var depth=0
        var quoted=false
        var escaped=false
        for(c in text) {
            if(quoted) {
                if(escaped) escaped=false else if(c=='\\') escaped=true else if(c=='"') quoted=false
            } else when(c) {
                '"' -> quoted=true
                '{','[' -> { depth++;require(depth<=16) { "Kayıt çok derin" } }
                '}',']' -> { depth--;require(depth>=0) { "Kayıt yapısı geçersiz" } }
            }
        }
        require(!quoted && depth==0) { "Kayıt yapısı tamamlanmamış" }
    }
}
