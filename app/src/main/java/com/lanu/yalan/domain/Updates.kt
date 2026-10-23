package com.lanu.yalan.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.URI
import java.security.MessageDigest

data class UpdateManifest(val versionCode: Long, val apkUrl: String, val sha256: String,
                          val apkBytes: Long, val versionName: String = "")
data class ApkIdentity(val packageName: String, val versionCode: Long, val signerSha256: Set<String>)
enum class UpdateFailure { METADATA, NETWORK, STORAGE, CHECKSUM, PACKAGE, SIGNATURE, VERSION }
class UpdateException(val reason: UpdateFailure, message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** The installed application's signing certificate remains the trust anchor for public release metadata. */
object UpdatePolicy {
    const val APPLICATION_ID = "com.lanu.yalan"
    const val OWNER = "kanunal99-jpg"
    const val REPOSITORY = "LANU-Yalan-n-Seveyim"
    const val MANIFEST_ASSET = "lanu-update.json"
    const val RELEASES_URL = "https://api.github.com/repos/$OWNER/$REPOSITORY/releases?per_page=10"
    const val MAX_RELEASES = 10
    const val MAX_MANIFEST_BYTES = 32 * 1024
    const val MAX_RELEASE_INDEX_BYTES = 1024 * 1024
    const val MAX_APK_BYTES = 512L * 1024 * 1024
    private val digestPattern = Regex("[0-9a-fA-F]{64}")
    private val segmentPattern = Regex("[A-Za-z0-9._-]{1,180}")
    private val json = Json { ignoreUnknownKeys = true }
    data class ReleaseManifest(val url: String, val apkAssets: Map<String, Long>)

    fun parseManifest(text: String): UpdateManifest = metadata {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST_BYTES)
        boundedJsonDepth(text)
        val value = json.parseToJsonElement(text) as? JsonObject ?: error("Manifest must be an object")
        fun number(key: String): Long {
            val item = value[key] as? JsonPrimitive ?: error("Missing number: $key")
            require(!item.isString)
            return item.longOrNull ?: error("Invalid number: $key")
        }
        fun string(key: String): String {
            val item = value[key] as? JsonPrimitive ?: error("Missing string: $key")
            require(item.isString)
            return item.content
        }
        validateManifest(UpdateManifest(number("versionCode"), string("apkUrl"), string("sha256"),
            number("apkBytes"), if (value.containsKey("versionName")) string("versionName") else ""))
    }

    /** Alpha prereleases are intentionally included; unpublished drafts are ignored. */
    fun parseReleaseManifests(text: String): List<ReleaseManifest> = metadata {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_RELEASE_INDEX_BYTES)
        boundedJsonDepth(text)
        val releases = json.parseToJsonElement(text) as? JsonArray ?: error("Expected release list")
        releases.take(MAX_RELEASES).mapNotNull releaseItem@ { element ->
            val release = element as? JsonObject ?: error("Invalid release")
            if ((release["draft"] as? JsonPrimitive)?.booleanOrNull != false) return@releaseItem null
            val assets = release["assets"] as? JsonArray ?: return@releaseItem null
            val parsed = assets.mapNotNull assetItem@ { assetElement ->
                val asset = assetElement as? JsonObject ?: return@assetItem null
                val name = (asset["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@assetItem null
                val url = (asset["browser_download_url"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@assetItem null
                val size = (asset["size"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: return@assetItem null
                Triple(name, url, size)
            }
            val manifest = parsed.singleOrNull { it.first == MANIFEST_ASSET } ?: return@releaseItem null
            requireReleaseAssetUrl(manifest.second, MANIFEST_ASSET)
            require(manifest.third in 1..MAX_MANIFEST_BYTES.toLong())
            val apks = parsed.filter { it.first.endsWith(".apk") }.associate { (_, url, size) ->
                requireReleaseAssetUrl(url, apk = true)
                require(size in 1..MAX_APK_BYTES)
                url to size
            }
            ReleaseManifest(manifest.second, apks)
        }
    }

    fun validateManifest(manifest: UpdateManifest): UpdateManifest = metadata {
        require(manifest.versionCode in 1..Int.MAX_VALUE.toLong())
        require(manifest.apkBytes in 1..MAX_APK_BYTES)
        require(digestPattern.matches(manifest.sha256))
        require(manifest.versionName.length <= 128 && manifest.versionName.none { it.isISOControl() })
        requireReleaseAssetUrl(manifest.apkUrl, apk = true)
        manifest.copy(sha256 = manifest.sha256.lowercase())
    }

    fun requireBelongsToRelease(manifest: UpdateManifest, release: ReleaseManifest) = metadata {
        require(release.apkAssets[manifest.apkUrl] == manifest.apkBytes)
        require(URI(manifest.apkUrl).path.substringBeforeLast('/') == URI(release.url).path.substringBeforeLast('/'))
    }

    fun requireNewer(manifest: UpdateManifest, installedVersionCode: Long) {
        if (manifest.versionCode <= installedVersionCode)
            throw UpdateException(UpdateFailure.VERSION, "Bu sürüm zaten yüklü veya daha eski. Mevcut sürüm korunuyor.")
    }

    fun verifyIdentity(manifest: UpdateManifest, installed: ApkIdentity, candidate: ApkIdentity) {
        requireNewer(manifest, installed.versionCode)
        if (installed.packageName != APPLICATION_ID || candidate.packageName != installed.packageName)
            throw UpdateException(UpdateFailure.PACKAGE, "Güncelleme bu uygulamaya ait değil. Kurulum engellendi.")
        if (candidate.versionCode != manifest.versionCode)
            throw UpdateException(UpdateFailure.VERSION, "APK sürümü güncelleme bilgisiyle eşleşmiyor. Kurulum engellendi.")
        val old = installed.signerSha256.map { it.lowercase() }.toSet()
        val next = candidate.signerSha256.map { it.lowercase() }.toSet()
        if (old.isEmpty() || next.isEmpty() || old.any { !digestPattern.matches(it) } ||
            next.any { !digestPattern.matches(it) } || old != next)
            throw UpdateException(UpdateFailure.SIGNATURE,
                "Güncellemenin imzası yüklü uygulamayla eşleşmiyor. İlerlemeni korumak için kurulum engellendi. Uygulamayı kaldırma.")
    }

    fun requireReleaseAssetUrl(url: String, filename: String? = null, apk: Boolean = false) = metadata {
        val uri = httpsUri(url)
        require(uri.host.equals("github.com", true) && uri.rawQuery == null)
        val parts = uri.path.split('/').drop(1)
        require(parts.size == 6 && parts[0].equals(OWNER, true) && parts[1].equals(REPOSITORY, true))
        require(parts[2] == "releases" && parts[3] == "download")
        require(parts.drop(4).all { segmentPattern.matches(it) && it != "." && it != ".." })
        require(!uri.rawPath.contains('%') && !uri.rawPath.contains('\\'))
        if (filename != null) require(parts.last() == filename)
        if (apk) require(parts.last().endsWith(".apk"))
    }

    fun requireDownloadRedirect(url: String) = metadata {
        val uri = httpsUri(url)
        when (uri.host.lowercase()) {
            "github.com" -> requireReleaseAssetUrl(url)
            "release-assets.githubusercontent.com", "objects.githubusercontent.com" -> Unit
            else -> error("Untrusted download host")
        }
    }

    private fun httpsUri(url: String): URI {
        require(url.length <= 8192)
        val uri = URI(url)
        require(uri.scheme.equals("https", true) && uri.host != null)
        require(uri.userInfo == null && uri.rawFragment == null && uri.port in listOf(-1, 443))
        return uri
    }
    private inline fun <T> metadata(block: () -> T): T = try { block() }
    catch (error: UpdateException) { throw error }
    catch (error: Exception) { throw UpdateException(UpdateFailure.METADATA,
        "Güncelleme bilgisi doğrulanamadı. Daha sonra yeniden dene.", error) }
    private fun boundedJsonDepth(text: String) {
        var depth = 0; var quoted = false; var escaped = false
        for (char in text) {
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 16) }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(depth == 0 && !quoted)
    }
}

object UpdateIntegrity {
    fun verify(file: File, manifest: UpdateManifest, checkActive: () -> Unit = {}) {
        if (!file.isFile || file.length() != manifest.apkBytes)
            throw UpdateException(UpdateFailure.CHECKSUM, "Güncelleme dosyasının boyutu doğrulanamadı. Yeniden indir.")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024); var total = 0L
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > manifest.apkBytes) throw UpdateException(UpdateFailure.CHECKSUM,
                    "Güncelleme dosyasının boyutu değişti. Yeniden indir.")
                digest.update(buffer, 0, count)
            }
            if (total != manifest.apkBytes || !MessageDigest.isEqual(digest.digest(), hexBytes(manifest.sha256)))
                throw UpdateException(UpdateFailure.CHECKSUM, "Güncelleme dosyası bozuk veya değiştirilmiş. Yeniden indir.")
        }
    }
    private fun hexBytes(hex: String): ByteArray {
        if (!Regex("[0-9a-fA-F]{64}").matches(hex)) throw UpdateException(UpdateFailure.METADATA, "Güncelleme özeti geçersiz.")
        return ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

object UpdateTransferPolicy {
    data class Range(val append: Boolean, val firstByte: Long, val responseBytes: Long)
    fun responseRange(status: Int, contentRange: String?, contentLength: Long, offset: Long, total: Long): Range {
        require(total in 1..UpdatePolicy.MAX_APK_BYTES && offset in 0..total)
        val range = when (status) {
            200 -> Range(false, 0, total)
            206 -> {
                val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(contentRange.orEmpty()) ?: failure()
                val (start, end, size) = match.destructured
                val first = start.toLongOrNull(); val last = end.toLongOrNull()
                if (first != offset || last == null || last < offset || last >= total || size.toLongOrNull() != total) failure()
                Range(true, offset, last - offset + 1)
            }
            else -> failure()
        }
        if (contentLength >= 0 && contentLength != range.responseBytes) failure()
        return range
    }
    private fun failure(): Nothing = throw UpdateException(UpdateFailure.NETWORK,
        "İndirme aralığı veya dosya boyutu doğrulanamadı. Yeniden dene.")
}
