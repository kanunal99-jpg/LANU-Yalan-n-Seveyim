package com.lanu.yalan.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lanu.yalan.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** User initiated GitHub Releases updates. Game saves and model files are never read or modified. */
@Singleton
class OtaUpdateStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val directory = File(context.cacheDir, "updates")
    private val mutex = Mutex()
    private val verifier = AndroidApkVerifier(context)
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.MINUTES).followRedirects(false).followSslRedirects(false).build()
    val installedVersionCode: Long get() = verifier.installedIdentity().versionCode

    suspend fun check(): UpdateManifest? = withContext(Dispatchers.IO) {
        networkErrors {
            val releases = withResponse(UpdatePolicy.RELEASES_URL, releaseIndex = true) { response ->
                if (response.code == 404) emptyList() else {
                    requireSuccess(response)
                    UpdatePolicy.parseReleaseManifests(readBounded(response, UpdatePolicy.MAX_RELEASE_INDEX_BYTES))
                }
            }
            var newest: UpdateManifest? = null
            val installed = installedVersionCode
            for (release in releases) {
                currentCoroutineContext().ensureActive()
                val manifest = withResponse(release.url) { response ->
                    requireSuccess(response)
                    UpdatePolicy.parseManifest(readBounded(response, UpdatePolicy.MAX_MANIFEST_BYTES))
                }
                UpdatePolicy.requireBelongsToRelease(manifest, release)
                if (manifest.versionCode > installed && manifest.versionCode > (newest?.versionCode ?: 0L)) newest = manifest
            }
            newest
        }
    }

    /** Cancellation preserves a version/hash-specific .part; no cached bytes are trusted before verification. */
    suspend fun download(manifest: UpdateManifest, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            val update = UpdatePolicy.validateManifest(manifest)
            UpdatePolicy.requireNewer(update, installedVersionCode)
            ensureDirectory()
            val complete = completeFile(update)
            if (complete.isFile) {
                try {
                    verify(complete, update)
                    onProgress(update.apkBytes, update.apkBytes)
                    return@withLock complete
                } catch (error: UpdateException) { complete.delete() }
            }
            val partial = File(directory, complete.name + ".part")
            directory.listFiles()?.filter { it != partial && it != complete }?.forEach { it.delete() }
            if (partial.length() > update.apkBytes && !partial.delete())
                throw UpdateException(UpdateFailure.STORAGE, "Yarım güncelleme dosyası temizlenemedi.")
            val offset = partial.length()
            val needed = update.apkBytes - offset
            if (needed > 0 && directory.usableSpace < needed + 8L * 1024 * 1024)
                throw UpdateException(UpdateFailure.STORAGE, "Güncelleme için yeterli boş alan yok. Yer açıp yeniden dene.")
            onProgress(offset, update.apkBytes)
            if (offset < update.apkBytes) networkErrors {
                withResponse(update.apkUrl, offset = offset) { response ->
                    requireSuccess(response)
                    val body = response.body ?: throw UpdateException(UpdateFailure.NETWORK, "Güncelleme yanıtı boş.")
                    val range = UpdateTransferPolicy.responseRange(response.code, response.header("Content-Range"),
                        body.contentLength(), offset, update.apkBytes)
                    val expectedResponseBytes = range.responseBytes
                    var total = range.firstByte
                    var received = 0L
                    if (!range.append) onProgress(0, update.apkBytes)
                    body.byteStream().use { input ->
                        FileOutputStream(partial, range.append).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var lastProgress = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), expectedResponseBytes - received + 1).toInt())
                                if (count < 0) break
                                received += count; total += count
                                if (received > expectedResponseBytes || total > update.apkBytes)
                                    throw UpdateException(UpdateFailure.NETWORK, "Güncelleme dosyası beklenenden büyük. İndirme durduruldu.")
                                output.write(buffer, 0, count)
                                val now = System.nanoTime()
                                if (now - lastProgress >= 200_000_000L) {
                                    onProgress(total, update.apkBytes); lastProgress = now
                                }
                            }
                            output.fd.sync()
                        }
                    }
                }
            }
            if (partial.length() != update.apkBytes)
                throw UpdateException(UpdateFailure.NETWORK, "İndirme tamamlanmadı. Devam etmek için yeniden dene.")
            onProgress(update.apkBytes, update.apkBytes)
            try { verify(partial, update) } catch (error: UpdateException) { partial.delete(); throw error }
            currentCoroutineContext().ensureActive()
            if (!partial.renameTo(complete)) throw UpdateException(UpdateFailure.STORAGE, "Doğrulanan güncelleme dosyası kaydedilemedi.")
            complete
        }
    }

    suspend fun verifiedApk(manifest: UpdateManifest): File? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val update = UpdatePolicy.validateManifest(manifest)
            val file = completeFile(update)
            if (!file.isFile) null else try { verify(file, update); file }
            catch (error: UpdateException) { file.delete(); null }
        }
    }

    /** Repeat verification immediately before sharing an installer URI, including after the settings round trip. */
    suspend fun installIntent(manifest: UpdateManifest): Intent = withContext(Dispatchers.IO) {
        mutex.withLock {
            val update = UpdatePolicy.validateManifest(manifest)
            val file = completeFile(update)
            if (!file.isFile) throw UpdateException(UpdateFailure.STORAGE, "Güncelleme dosyası bulunamadı. Yeniden indir.")
            verify(file, update)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private suspend fun verify(file: File, manifest: UpdateManifest) {
        val coroutine = currentCoroutineContext()
        UpdateIntegrity.verify(file, manifest) { coroutine.ensureActive() }
        coroutine.ensureActive()
        verifier.verify(file, manifest)
    }
    private fun completeFile(manifest: UpdateManifest) = File(directory, "${manifest.versionCode}-${manifest.sha256}.apk")
    private fun ensureDirectory() {
        if ((!directory.isDirectory && !directory.mkdirs()) || !directory.isDirectory)
            throw UpdateException(UpdateFailure.STORAGE, "Güncelleme klasörü oluşturulamadı.")
    }
    private suspend fun readBounded(response: Response, maximum: Int): String {
        val body = response.body ?: throw UpdateException(UpdateFailure.NETWORK, "Güncelleme bilgisi alınamadı.")
        if (body.contentLength() > maximum) throw UpdateException(UpdateFailure.METADATA, "Güncelleme bilgisi izin verilen boyutu aşıyor.")
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(4096)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer, 0, minOf(buffer.size, maximum - output.size() + 1))
                if (count < 0) break
                output.write(buffer, 0, count)
                if (output.size() > maximum) throw UpdateException(UpdateFailure.METADATA, "Güncelleme bilgisi izin verilen boyutu aşıyor.")
            }
        }
        return try { output.toByteArray().decodeToString(throwOnInvalidSequence = true) }
        catch (error: IllegalArgumentException) { throw UpdateException(UpdateFailure.METADATA, "Güncelleme metni geçersiz.", error) }
    }
    private fun requireSuccess(response: Response) {
        if (!response.isSuccessful) throw UpdateException(UpdateFailure.NETWORK,
            "Güncelleme sunucusuna ulaşılamadı (${response.code}). Bir süre sonra yeniden dene.")
    }

    private suspend fun <T> withResponse(url: String, offset: Long = 0L, releaseIndex: Boolean = false,
                                        consume: suspend (Response) -> T): T = coroutineScope {
        if (releaseIndex) check(url == UpdatePolicy.RELEASES_URL) else UpdatePolicy.requireReleaseAssetUrl(url)
        val activeCall = AtomicReference<Call?>()
        val canceller = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { activeCall.get()?.cancel() }
        }
        try {
            var target = url
            repeat(6) { hop ->
                currentCoroutineContext().ensureActive()
                val request = Request.Builder().url(target).header("User-Agent", "LANU-Android-Updater")
                    .header("Accept-Encoding", "identity").header("Cache-Control", "no-cache").apply {
                        if (releaseIndex) header("Accept", "application/vnd.github+json")
                        if (offset > 0) header("Range", "bytes=$offset-")
                    }.build()
                val call = http.newCall(request)
                activeCall.set(call)
                currentCoroutineContext().ensureActive()
                val response = call.execute()
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    target = response.use {
                        val location = it.header("Location") ?: throw UpdateException(UpdateFailure.NETWORK, "Güncelleme indirme yönlendirmesi geçersiz.")
                        it.request.url.resolve(location)?.toString() ?: throw UpdateException(UpdateFailure.NETWORK, "Güncelleme indirme adresi geçersiz.")
                    }
                    UpdatePolicy.requireDownloadRedirect(target)
                    if (hop == 5) throw UpdateException(UpdateFailure.NETWORK, "Güncelleme sunucusu çok fazla yönlendirme yaptı.")
                } else return@coroutineScope response.use { consume(it) }
            }
            error("Unreachable redirect state")
        } finally { canceller.cancel(); activeCall.get()?.cancel() }
    }
    private suspend fun <T> networkErrors(block: suspend () -> T): T = try { block() }
    catch (error: CancellationException) { throw error }
    catch (error: IOException) {
        currentCoroutineContext().ensureActive()
        throw UpdateException(UpdateFailure.NETWORK,
            "Güncelleme bağlantısı kesildi. İnternetini kontrol edip devam etmek için yeniden dene.", error)
    }
}
