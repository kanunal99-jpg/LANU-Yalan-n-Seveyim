package com.lanu.yalan.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.lanu.yalan.domain.ApkIdentity
import com.lanu.yalan.domain.UpdateException
import com.lanu.yalan.domain.UpdateFailure
import com.lanu.yalan.domain.UpdateManifest
import com.lanu.yalan.domain.UpdatePolicy
import java.io.File
import java.security.MessageDigest

/** Platform archive parsing verifies APK signatures, including v2 on Android 8+. */
class AndroidApkVerifier(private val context: Context) {
    @Suppress("DEPRECATION")
    private val flags: Int get() = if (Build.VERSION.SDK_INT >= 28)
        PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    fun installedIdentity(): ApkIdentity = identity(context.packageManager.getPackageInfo(context.packageName, flags))
    @Suppress("DEPRECATION")
    fun archiveIdentity(file: File): ApkIdentity {
        val info = try { context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) }
        catch (error: RuntimeException) { throw UpdateException(UpdateFailure.PACKAGE,
            "Güncelleme APK dosyası doğrulanamadı. Kurulum engellendi.", error) }
        if (info == null) throw UpdateException(UpdateFailure.PACKAGE,
            "Güncelleme APK dosyası açılamadı veya imzası geçersiz. Yeniden indir.")
        return identity(info)
    }
    fun verify(file: File, manifest: UpdateManifest) = UpdatePolicy.verifyIdentity(manifest, installedIdentity(), archiveIdentity(file))
    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): ApkIdentity {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val signers = signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
        if (signers.isEmpty()) throw UpdateException(UpdateFailure.SIGNATURE, "APK imzası doğrulanamadı. Kurulum engellendi.")
        val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return ApkIdentity(info.packageName, version, signers)
    }
}
