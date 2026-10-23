package com.lanu.yalan

import android.content.ContextWrapper
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lanu.yalan.data.DeviceInfo
import com.lanu.yalan.data.PerformanceMonitor
import com.lanu.yalan.domain.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses an isolated files directory; it never writes to the player's progress or metrics. */
@RunWith(AndroidJUnit4::class)
class PerformancePersistenceTest {
    @Before fun requireControlledEmulator() {
        assumeTrue("Performance smoke requires UI_SMOKE", BuildConfig.UI_SMOKE)
        assumeTrue("Never mutate a physical phone", Build.HARDWARE in setOf("ranchu", "goldfish"))
        assumeTrue("CI file mutations require opt-in", InstrumentationRegistry.getArguments().getString("lanu.allowProgressReset") == "true")
    }
    @Test fun metricsSurviveRestartAndCorruptionCannotBlockNewMeasurements() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"performance-test").apply { deleteRecursively();mkdirs() }
        val isolated=object : ContextWrapper(context) { override fun getFilesDir(): File = directory }
        try {
            val first=PerformanceMonitor(isolated,DeviceInfo(context))
            first.record(PlayMode.FAST,450)
            first.record(PlayMode.LOCAL_AI,30_000,TimingOutcome.TIMEOUT)
            val restored=PerformanceMonitor(isolated,DeviceInfo(context)).load()
            assertEquals(1,restored.fast.successCount)
            assertEquals(450L,restored.fast.p95Ms)
            assertEquals(1,restored.ai.timeoutCount)
            assertNull(restored.ai.p95Ms)
            val bytes=File(directory,"performance.json").readText()
            assertFalse(bytes.contains("answer"))
            assertFalse(bytes.contains("topic"))
            File(directory,"performance.json").writeText("x".repeat(70_000))
            val recovering=PerformanceMonitor(isolated,DeviceInfo(context))
            assertFalse(recovering.load().storageAvailable)
            val resumed=recovering.record(PlayMode.FAST,500)
            assertTrue(resumed.storageAvailable)
            assertEquals(500L,resumed.fast.p95Ms)
        } finally { directory.deleteRecursively() }
    }
}
