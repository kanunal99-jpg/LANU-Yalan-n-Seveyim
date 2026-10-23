package com.lanu.yalan.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

/** SAF documents contain only the already encrypted portable backup. */
class BackupDocuments @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        require(uri.scheme=="content") { "Yedek için belge seçiciden bir dosya seç." }
        val input=context.contentResolver.openInputStream(uri) ?: throw IOException("Yedek dosyası açılamadı.")
        input.use { stream ->
            val output=ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            while(true) {
                ensureActive()
                val n=stream.read(buffer)
                if(n<0) break
                if(n==0) continue
                if(output.size()+n>PortableBackup.MAX_FILE_BYTES) throw BackupException(BackupFailure.FORMAT)
                output.write(buffer,0,n)
            }
            output.toByteArray()
        }
    }
    suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        require(uri.scheme=="content" && bytes.size in 1..PortableBackup.MAX_FILE_BYTES)
        val output=context.contentResolver.openOutputStream(uri,"wt") ?: throw IOException("Yedek dosyası yazılamadı.")
        output.use { stream -> ensureActive();stream.write(bytes);stream.flush() }
        ensureActive()
        val actual=read(uri)
        if(!MessageDigest.isEqual(bytes,actual)) throw IOException("Yedek dosyası tam kaydedilemedi. Başka bir konuma yeniden kaydet.")
    }
}
