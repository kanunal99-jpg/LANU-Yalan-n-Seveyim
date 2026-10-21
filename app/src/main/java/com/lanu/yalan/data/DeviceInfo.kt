package com.lanu.yalan.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.lanu.yalan.BuildConfig
import com.lanu.yalan.domain.DevicePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceProfile(val androidVersion: String, val memoryClassGb: Int, val inferenceThreads: Int) {
    val summary: String get() = "Android " + androidVersion + " · " + memoryClassGb + " GB RAM sınıfı"
}
class DeviceNotReadyException(message: String) : IllegalStateException(message)

@Singleton
class DeviceInfo @Inject constructor(@ApplicationContext private val context: Context) {
    private fun memory(): ActivityManager.MemoryInfo {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
    }
    fun snapshot(): DeviceProfile {
        val memory = memory()
        return DeviceProfile(Build.VERSION.RELEASE, DevicePolicy.memoryClassGb(memory.totalMem),
            DevicePolicy.inferenceThreads(Runtime.getRuntime().availableProcessors(), memory.totalMem))
    }
    fun ensureInferenceReady(checkMemory: Boolean = true): DeviceProfile {
        if (BuildConfig.UI_SMOKE) throw DeviceNotReadyException("Bu paket yalnızca otomatik arayüz testi içindir.")
        if (Build.VERSION.SDK_INT < 26 || !Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
            throw DeviceNotReadyException("Cihaz içi yapay zekâ için Android 8 veya üzeri ve 64 bit Android gerekiyor.")
        }
        // A resident model itself consumes RAM. Do not reject every subsequent turn for that.
        if (checkMemory && memory().lowMemory) {
            throw DeviceNotReadyException("Telefonun belleği şu anda dolu. Diğer uygulamaları kapatıp yeniden dene; ilerlemen korunuyor.")
        }
        return snapshot()
    }
}
