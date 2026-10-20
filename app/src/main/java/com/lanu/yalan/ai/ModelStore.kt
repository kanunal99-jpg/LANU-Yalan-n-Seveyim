package com.lanu.yalan.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelStore @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.noBackupFilesDir, "brain").apply { mkdirs() }
    val model = File(directory, "qwen35-4b-q4.gguf")
    private val partial = File(directory, "qwen35-4b.part")
    private val verified = File(directory, "verified")
    private val mutex = Mutex()
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).build()
    fun ready() = model.length() == SIZE && verified.exists() && verified.readText() == HASH
    suspend fun delete() = withContext(Dispatchers.IO) { mutex.withLock {
        model.delete(); partial.delete(); verified.delete()
    } }
    suspend fun download(progress: (Float, String) -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (ready()) return@withLock
            if (partial.length() > SIZE) partial.delete()
            val offset = partial.length()
            check(directory.usableSpace > SIZE - offset + 150_000_000L) { "İndirme için yaklaşık 3 GB boş alan gerekiyor." }
            if (offset < SIZE) {
                val request = Request.Builder().url(URL).apply { if(offset > 0) header("Range", "bytes=$offset-") }.build()
                val call = http.newCall(request)
                try {
                    call.execute().use { response ->
                        check(response.isSuccessful) { "İndirme sunucusuna ulaşılamadı (${response.code}). Daha sonra yeniden dene." }
                        val append = response.code == 206 && offset > 0
                        if (response.code == 206) check(response.header("Content-Range")?.startsWith("bytes ${if(append) offset else 0}-") == true) { "İndirme aralığı doğrulanamadı." }
                        var total = if(append) offset else 0L
                        val body = checkNotNull(response.body)
                        body.byteStream().use { input -> FileOutputStream(partial, append).use { output ->
                            val buffer = ByteArray(256 * 1024)
                            var last = 0L
                            while(true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                total += n
                                check(total <= SIZE) { "Model boyutu beklenenden büyük." }
                                output.write(buffer, 0, n)
                                val now = System.currentTimeMillis()
                                if(now - last > 200) { progress(total.toFloat() / SIZE, "${total / 1_000_000} / 2741 MB"); last = now }
                            }
                            output.fd.sync()
                        } }
                    }
                } finally { call.cancel() }
            }
            check(partial.length() == SIZE) { "İndirme tamamlanmadı. Devam etmek için yeniden dene." }
            progress(1f, "Dosya bütünlüğü doğrulanıyor…")
            val digest = MessageDigest.getInstance("SHA-256")
            partial.inputStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                while(true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if(n < 0) break; digest.update(buffer, 0, n) }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != HASH) { partial.delete(); error("Dosya doğrulanamadı. Lütfen yeniden indir.") }
            check(partial.renameTo(model)) { "Model kaydedilemedi." }
            verified.writeText(HASH)
            progress(1f, "Oyun beyni hazır")
        }
    }
    companion object {
        const val SIZE = 2740937888L
        const val HASH = "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4"
        const val URL = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/e87f176479d0855a907a41277aca2f8ee7a09523/Qwen3.5-4B-Q4_K_M.gguf?download=true"
    }
}
