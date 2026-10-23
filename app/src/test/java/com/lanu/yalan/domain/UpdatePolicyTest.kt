package com.lanu.yalan.domain

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class UpdatePolicyTest {
    @get:Rule val temporary = TemporaryFolder()
    private val base = "https://github.com/${UpdatePolicy.OWNER}/${UpdatePolicy.REPOSITORY}/releases/download/v0.4.0"
    private val hash = "a".repeat(64)
    private fun manifest() = UpdateManifest(5, "$base/LANU.apk", hash, 100, "0.4.0-alpha")
    private fun json(version: String = "5", size: String = "100", checksum: String = hash, url: String = "$base/LANU.apk") =
        """{"versionCode":$version,"versionName":"0.4.0-alpha","apkUrl":"$url","sha256":"$checksum","apkBytes":$size}"""

    @Test fun parsesContractAndUsesCodeInsteadOfDisplayNameForVersionOrdering() {
        val parsed = UpdatePolicy.parseManifest(json(checksum = hash.uppercase()))
        assertEquals(manifest(), parsed)
        UpdatePolicy.requireNewer(parsed, 4)
        assertEquals(UpdateFailure.VERSION, assertThrows(UpdateException::class.java) {
            UpdatePolicy.requireNewer(parsed.copy(versionName = "999.0"), 5)
        }.reason)
    }
    @Test fun rejectsMissingQuotedNegativeOverflowAndOversizeMetadata() {
        listOf("{}", json(version = "\"5\""), json(version = "0"), json(version = "2147483648"),
            json(size = "0"), json(size = "${UpdatePolicy.MAX_APK_BYTES + 1}"), json(checksum = "ab"),
            "[".repeat(17) + "]".repeat(17), " ".repeat(UpdatePolicy.MAX_MANIFEST_BYTES + 1)).forEach { value ->
            assertEquals(UpdateFailure.METADATA, assertThrows(UpdateException::class.java) { UpdatePolicy.parseManifest(value) }.reason)
        }
    }
    @Test fun allowsOnlyHttpsApkAssetsInTheOfficialRepository() {
        UpdatePolicy.requireReleaseAssetUrl(manifest().apkUrl, apk = true)
        listOf(manifest().apkUrl.replace("https://", "http://"),
            manifest().apkUrl.replace("github.com", "github.com.evil.test"),
            manifest().apkUrl.replace("github.com", "github.com@evil.test"),
            manifest().apkUrl.replace("github.com", "user@github.com"),
            manifest().apkUrl.replace("github.com", "github.com:8443"),
            manifest().apkUrl.replace(UpdatePolicy.OWNER, "someone-else"),
            manifest().apkUrl.replace("/releases/download/", "/blob/"),
            manifest().apkUrl.replace("LANU.apk", "LANU.html"),
            manifest().apkUrl.replace("LANU.apk", "..%2FLANU.apk"),
            manifest().apkUrl.replace("v0.4.0", ".."),
            manifest().apkUrl + "?download=1", manifest().apkUrl + "#fragment").forEach { url ->
            assertThrows("Accepted untrusted URL: $url", UpdateException::class.java) { UpdatePolicy.requireReleaseAssetUrl(url, apk = true) }
        }
    }
    @Test fun redirectsRejectCredentialsDowngradesAndUnrelatedHosts() {
        UpdatePolicy.requireDownloadRedirect("https://release-assets.githubusercontent.com/asset?signature=opaque")
        UpdatePolicy.requireDownloadRedirect("https://objects.githubusercontent.com/asset")
        listOf("http://release-assets.githubusercontent.com/asset", "https://evil.test/a.apk",
            "https://release-assets.githubusercontent.com.evil.test/asset", "https://x@objects.githubusercontent.com/a",
            "https://github.com/unrelated/repo/releases/download/v1/a.apk").forEach { url ->
            assertThrows(UpdateException::class.java) { UpdatePolicy.requireDownloadRedirect(url) }
        }
    }
    @Test fun alphaReleasesAreIncludedAndManifestMustMatchReleaseApkAssetSizeAndTag() {
        val assets = """[{"name":"lanu-update.json","browser_download_url":"$base/lanu-update.json","size":300},{"name":"LANU.apk","browser_download_url":"$base/LANU.apk","size":100}]"""
        val releases = UpdatePolicy.parseReleaseManifests("""[{"draft":true,"assets":$assets},{"draft":false,"prerelease":true,"assets":$assets},{"draft":false,"assets":[]}]""")
        assertEquals(1, releases.size)
        UpdatePolicy.requireBelongsToRelease(manifest(), releases.single())
        assertThrows(UpdateException::class.java) { UpdatePolicy.requireBelongsToRelease(manifest().copy(apkBytes = 99), releases.single()) }
        val other = manifest().copy(apkUrl = manifest().apkUrl.replace("v0.4.0", "v0.3.0"))
        assertThrows(UpdateException::class.java) {
            UpdatePolicy.requireBelongsToRelease(other, releases.single().copy(apkAssets = mapOf(other.apkUrl to other.apkBytes)))
        }
    }
    @Test fun completeSignerSetsAcceptAnUpgradeRegardlessOfSignerOrder() {
        val second = "b".repeat(64)
        UpdatePolicy.verifyIdentity(manifest(), ApkIdentity(UpdatePolicy.APPLICATION_ID, 4, linkedSetOf(hash, second)),
            ApkIdentity(UpdatePolicy.APPLICATION_ID, 5, linkedSetOf(second.uppercase(), hash)))
    }
    @Test fun packageVersionAndSignatureMismatchesFailBeforeInstallation() {
        val installed = ApkIdentity(UpdatePolicy.APPLICATION_ID, 4, setOf(hash))
        val candidate = installed.copy(versionCode = 5)
        fun reason(value: ApkIdentity) = assertThrows(UpdateException::class.java) {
            UpdatePolicy.verifyIdentity(manifest(), installed, value)
        }.reason
        assertEquals(UpdateFailure.PACKAGE, reason(candidate.copy(packageName = "com.someone.else")))
        assertEquals(UpdateFailure.VERSION, reason(candidate.copy(versionCode = 6)))
        assertEquals(UpdateFailure.SIGNATURE, reason(candidate.copy(signerSha256 = setOf("b".repeat(64)))))
        assertEquals(UpdateFailure.SIGNATURE, reason(candidate.copy(signerSha256 = setOf(hash, "b".repeat(64)))))
        assertEquals(UpdateFailure.SIGNATURE, reason(candidate.copy(signerSha256 = emptySet())))
        assertEquals(UpdateFailure.SIGNATURE, reason(candidate.copy(signerSha256 = setOf("bad-cert"))))
        assertEquals(UpdateFailure.VERSION, assertThrows(UpdateException::class.java) {
            UpdatePolicy.verifyIdentity(manifest().copy(versionCode = 4), installed, installed)
        }.reason)
    }
    @Test fun actualBytesRejectEqualSizeCorruptionAndIncompleteOrOversizeFiles() {
        val file = temporary.newFile("update.apk")
        val bytes = "Verified APK content".toByteArray()
        val checksum = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val update = manifest().copy(apkBytes = bytes.size.toLong(), sha256 = checksum)
        file.writeBytes(bytes); UpdateIntegrity.verify(file, update)
        listOf(bytes.copyOf().also { it[0] = (it[0] + 1).toByte() }, bytes.copyOf(bytes.size - 1), bytes + byteArrayOf(0)).forEach {
            file.writeBytes(it)
            assertEquals(UpdateFailure.CHECKSUM, assertThrows(UpdateException::class.java) { UpdateIntegrity.verify(file, update) }.reason)
        }
    }
    @Test fun checksumVerificationRemainsCancellable() {
        val file = temporary.newFile("cancel.apk").apply { writeBytes(ByteArray(100)) }
        assertThrows(CancellationException::class.java) { UpdateIntegrity.verify(file, manifest()) { throw CancellationException("cancel") } }
    }
    @Test fun validPartialResponseResumesAndIgnoredRangeRestartsAtZero() {
        assertEquals(UpdateTransferPolicy.Range(true, 40, 60), UpdateTransferPolicy.responseRange(206, "bytes 40-99/100", 60, 40, 100))
        assertEquals(UpdateTransferPolicy.Range(false, 0, 100), UpdateTransferPolicy.responseRange(200, null, 100, 40, 100))
        assertEquals(UpdateTransferPolicy.Range(true, 40, 20), UpdateTransferPolicy.responseRange(206, "bytes 40-59/100", -1, 40, 100))
    }
    @Test fun invalidPartialRangesAndBodyLengthsAreNeverAppended() {
        listOf("bytes 0-99/100", "bytes 40-100/100", "bytes 40-99/101", "bytes 40-39/100", "bytes 40-99/*",
            "bytes 999999999999999999999999-99/100", "malformed").forEach { range ->
            assertEquals(UpdateFailure.NETWORK, assertThrows(UpdateException::class.java) {
                UpdateTransferPolicy.responseRange(206, range, -1, 40, 100)
            }.reason)
        }
        assertThrows(UpdateException::class.java) { UpdateTransferPolicy.responseRange(206, "bytes 40-99/100", 59, 40, 100) }
        assertThrows(UpdateException::class.java) { UpdateTransferPolicy.responseRange(200, null, 101, 40, 100) }
        assertThrows(UpdateException::class.java) { UpdateTransferPolicy.responseRange(416, null, -1, 40, 100) }
    }
}
