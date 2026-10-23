package com.lanu.yalan.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.BatteryManager
import android.os.PowerManager
import com.lanu.yalan.BuildConfig
import com.lanu.yalan.domain.DevicePolicy
import com.lanu.yalan.domain.RuntimeHealth
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
    fun runtimeHealth(): RuntimeHealth {
        val memory=memory()
        val power=context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val battery=context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val thermal=if(Build.VERSION.SDK_INT>=29) try { power.currentThermalStatus } catch(_: RuntimeException) { null } else null
        val batteryPercent=try { battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } } catch(_: RuntimeException) { null }
        return RuntimeHealth(memory.availMem/(1024*1024),memory.totalMem/(1024*1024),memory.lowMemory,
            thermal,try { power.isPowerSaveMode } catch(_: RuntimeException) { false },batteryPercent)
    }
    fun snapshot(): DeviceProfile {
        return profile(runtimeHealth())
    }
    private fun profile(health: RuntimeHealth): DeviceProfile {
        val totalMemoryBytes=health.totalMemoryMb*1024*1024
        return DeviceProfile(Build.VERSION.RELEASE, DevicePolicy.memoryClassGb(totalMemoryBytes),
            DevicePolicy.inferenceThreads(Runtime.getRuntime().availableProcessors(),totalMemoryBytes,health.thermalStatus,health.powerSave))
    }
    fun ensureInferenceReady(checkMemory: Boolean = true): DeviceProfile {
        if (BuildConfig.UI_SMOKE) throw DeviceNotReadyException("Bu paket yalnızca otomatik arayüz testi içindir.")
        if (Build.VERSION.SDK_INT < 26 || !Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
            throw DeviceNotReadyException("Cihaz içi yapay zekâ için Android 8 veya üzeri ve 64 bit Android gerekiyor.")
        }
        // A resident model itself consumes RAM. Do not reject every subsequent turn for that.
        val health=runtimeHealth()
        if (checkMemory && health.lowMemory) {
            throw DeviceNotReadyException("Telefonun belleği şu anda dolu. Diğer uygulamaları kapatıp yeniden dene; ilerlemen korunuyor.")
        }
        if(DevicePolicy.shouldDeferInference(health.thermalStatus)) {
            throw DeviceNotReadyException("Telefon şu anda çok sıcak. Biraz soğuduktan sonra yeniden dene; cevabın ve ilerlemen korunuyor.")
        }
        return profile(health)
    }
}
