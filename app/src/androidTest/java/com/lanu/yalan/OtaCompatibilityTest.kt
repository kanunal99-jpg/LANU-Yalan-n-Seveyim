package com.lanu.yalan

import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lanu.yalan.data.AndroidApkVerifier
import com.lanu.yalan.data.OtaUpdateStore
import com.lanu.yalan.data.SaveStore
import com.lanu.yalan.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Runs only on an opted-in UI_SMOKE emulator. No physical device data may be modified. */
@RunWith(AndroidJUnit4::class)
class OtaCompatibilityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val arguments get() = InstrumentationRegistry.getArguments()
    private fun controlledEmulator() {
        assumeTrue("OTA smoke requires UI_SMOKE", BuildConfig.UI_SMOKE)
        assumeTrue("OTA smoke must never alter a physical phone", Build.HARDWARE in setOf("ranchu", "goldfish"))
        assumeTrue("CI mutations require explicit opt-in", arguments.getString("lanu.allowProgressReset") == "true")
    }

    @Test fun candidateUsesProductionVerificationAndInstallerProvider() = runBlocking {
        controlledEmulator()
        assumeTrue(arguments.getString("lanu.upgradePhase") == null)
        val source = arguments.getString("lanu.otaCandidate")
        assumeTrue("Supply same-key next-version APK with lanu.otaCandidate", !source.isNullOrBlank())
        val candidate = copyCandidateFromShell(requireNotNull(source))
        val tampered = File(context.cacheDir, "ota-tampered-test.apk")
        var cached: File? = null
        try {
            val verifier = AndroidApkVerifier(context)
            val installed = verifier.installedIdentity()
            val identity = verifier.archiveIdentity(candidate)
            assertTrue("Candidate must have a newer versionCode", identity.versionCode > installed.versionCode)
            val manifest = manifestFor(candidate, identity.versionCode)
            UpdateIntegrity.verify(candidate, manifest)
            verifier.verify(candidate, manifest)
            assertEquals(installed.packageName, identity.packageName)
            assertEquals(installed.signerSha256, identity.signerSha256)
            val installedApk = File(context.applicationInfo.sourceDir)
            assertEquals(UpdateFailure.VERSION, assertThrows(UpdateException::class.java) {
                verifier.verify(installedApk, manifestFor(installedApk, installed.versionCode))
            }.reason)
            assertEquals(UpdateFailure.PACKAGE, assertThrows(UpdateException::class.java) {
                UpdatePolicy.verifyIdentity(manifest, installed, identity.copy(packageName = "com.example.unrelated"))
            }.reason)
            assertEquals(UpdateFailure.SIGNATURE, assertThrows(UpdateException::class.java) {
                UpdatePolicy.verifyIdentity(manifest, installed, identity.copy(signerSha256 = setOf("b".repeat(64))))
            }.reason)
            candidate.copyTo(tampered, overwrite = true)
            RandomAccessFile(tampered, "rw").use {
                val middle = it.length() / 2; it.seek(middle)
                val original = it.readByte(); it.seek(middle); it.writeByte(original.toInt() xor 1)
            }
            assertEquals(UpdateFailure.CHECKSUM, assertThrows(UpdateException::class.java) { UpdateIntegrity.verify(tampered, manifest) }.reason)
            // A replaced public checksum must not bypass cryptographic APK signature verification.
            val invalid = runCatching { verifier.verify(tampered, manifestFor(tampered, identity.versionCode)) }.exceptionOrNull()
            assertTrue(invalid is UpdateException)
            assertTrue((invalid as UpdateException).reason in setOf(UpdateFailure.PACKAGE, UpdateFailure.SIGNATURE))
            cached = File(context.cacheDir, "updates/${manifest.versionCode}-${manifest.sha256}.apk")
                .apply { parentFile!!.mkdirs(); candidate.copyTo(this, overwrite = true) }
            val store = OtaUpdateStore(context)
            assertEquals(cached, store.verifiedApk(manifest))
            val intent = store.installIntent(manifest)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertEquals("content", intent.data?.scheme)
            assertEquals("${context.packageName}.updates", intent.data?.authority)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val provider = context.packageManager.resolveContentProvider("${context.packageName}.updates", 0)
            assertNotNull(provider); assertFalse(provider!!.exported)
            context.contentResolver.openInputStream(requireNotNull(intent.data)).use { input ->
                assertNotNull(input)
                assertEquals(candidate.inputStream().use { it.read() }, input!!.read())
            }
            tampered.copyTo(requireNotNull(cached), overwrite = true)
            assertNull(store.verifiedApk(manifest))
            val installError = runCatching { store.installIntent(manifest) }.exceptionOrNull()
            assertTrue(installError is UpdateException)
            assertEquals(UpdateFailure.STORAGE, (installError as UpdateException).reason)
            println("LANU_OTA_VERIFIED old=${installed.versionCode} new=${identity.versionCode} same_signers=true provider=content")
        } finally { candidate.delete(); tampered.delete(); cached?.delete() }
    }

    @Test fun prepareUpgrade() = runBlocking {
        controlledEmulator()
        assumeTrue(arguments.getString("lanu.upgradePhase") == "prepare")
        val identity = AndroidApkVerifier(context).installedIdentity()
        SaveStore(context).write(upgradeFixture())
        assertEquals(upgradeFixture(), SaveStore(context).read())
        File(context.filesDir, "ota-upgrade-smoke-proof.txt")
            .writeText("${identity.versionCode}\n${identity.signerSha256.sorted().joinToString(",")}")
        println("LANU_OTA_PREPARED version=${identity.versionCode} unlocked=3")
    }
    @Test fun afterUpgradeReadsProgress() = runBlocking {
        controlledEmulator()
        assumeTrue(arguments.getString("lanu.upgradePhase") == "verify")
        val expected = requireNotNull(arguments.getString("lanu.expectedVersionCode")).toLong()
        val identity = AndroidApkVerifier(context).installedIdentity()
        val proof = File(context.filesDir, "ota-upgrade-smoke-proof.txt").readLines()
        assertEquals("Actual installed package version", expected, identity.versionCode)
        assertTrue(identity.versionCode > proof[0].toLong())
        assertEquals(proof[1], identity.signerSha256.sorted().joinToString(","))
        assertEquals("Encrypted progress must survive real package replacement", upgradeFixture(), SaveStore(context).read())
        println("LANU_OTA_UPGRADE_PRESERVED version=${identity.versionCode} unlocked=3 best=2 session=kept")
    }
    private fun upgradeFixture() = GameSave(unlocked = 3, best = mapOf(1 to 83, 2 to 77), spicy = false,
        onboarding = true, mode = PlayMode.FAST, recentJokeKeys = listOf("ota-before-upgrade"),
        session = Session(id = "ota-upgrade-preserved-story", level = 3, topic = "İşe geç kaldım",
            opening = "Tam olarak ne oldu?", mode = PlayMode.FAST))
    private fun manifestFor(file: File, versionCode: Long): UpdateManifest {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return UpdateManifest(versionCode,
            "https://github.com/${UpdatePolicy.OWNER}/${UpdatePolicy.REPOSITORY}/releases/download/v-ota-smoke/LANU.apk",
            digest.digest().joinToString("") { "%02x".format(it) }, file.length(), "OTA smoke")
    }
    private fun copyCandidateFromShell(path: String): File {
        require(Regex("/data/local/tmp/[A-Za-z0-9._-]+\\.apk").matches(path))
        val output = File(context.cacheDir, "ota-candidate-test.apk")
        val descriptor = instrumentation.uiAutomation.executeShellCommand("cat $path")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input -> output.outputStream().use { stream ->
            val buffer = ByteArray(128 * 1024); var total = 0L
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                total += n; require(total <= UpdatePolicy.MAX_APK_BYTES); stream.write(buffer, 0, n)
            }
            require(total > 32) { "Candidate APK could not be read from shell staging" }
        } }
        return output
    }
}
