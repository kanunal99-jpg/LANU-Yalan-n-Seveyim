package com.lanu.yalan.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.lanu.yalan.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Only timings/outcome enums are persisted. Never accepts a topic, answer or device identifier. */
@Singleton
class PerformanceMonitor @Inject constructor(
    @ApplicationContext context: Context,
    private val devices: DeviceInfo
) {
    private val file=AtomicFile(File(context.filesDir,"performance.json"))
    private val lock=Mutex()
    private val mutable=MutableStateFlow(PerformanceSnapshot())
    val state: StateFlow<PerformanceSnapshot> = mutable.asStateFlow()
    private var data=PerformanceData()
    private var loaded=false
    private var storageAvailable=true

    suspend fun load(): PerformanceSnapshot = withContext(Dispatchers.IO) {
        lock.withLock { ensureLoaded(); publish() }
    }
    suspend fun record(mode: PlayMode, elapsedMs: Long, outcome: TimingOutcome = TimingOutcome.SUCCESS,
                       phase: TimingPhase = TimingPhase.REPLY): PerformanceSnapshot = withContext(Dispatchers.IO) {
        lock.withLock {
            ensureLoaded()
            if(elapsedMs !in 0..PerformanceStats.MAX_ELAPSED_MS) {
                Log.w("LANU_Perf","timing_out_of_range")
                return@withLock publish()
            }
            data=PerformanceStats.record(data,TimingSample(mode,elapsedMs,outcome,phase))
            var output: java.io.FileOutputStream?=null
            try {
                output=file.startWrite()
                output.write(Json.encodeToString(data).toByteArray(Charsets.UTF_8))
                file.finishWrite(output)
                storageAvailable=true
            } catch(_: IOException) {
                unavailable(output)
            } catch(_: SecurityException) {
                unavailable(output)
            }
            publish()
        }
    }
    private fun ensureLoaded() {
        if(loaded) return
        try {
            val bytes=file.openRead().use { input ->
                val buffer=ByteArray(65_537)
                var count=0
                while(count<buffer.size) {
                    val read=input.read(buffer,count,buffer.size-count)
                    if(read<0) break
                    count+=read
                }
                buffer.copyOf(count)
            }
            require(bytes.size<=65_536)
            data=PerformanceStats.validated(Json.decodeFromString<PerformanceData>(bytes.toString(Charsets.UTF_8)))
        } catch(_: FileNotFoundException) {
            data=PerformanceData()
        } catch(_: IOException) {
            data=PerformanceData();storageAvailable=false
            Log.w("LANU_Perf","timings_read_failed")
        } catch(_: SecurityException) {
            data=PerformanceData();storageAvailable=false
            Log.w("LANU_Perf","timings_read_failed")
        } catch(_: IllegalArgumentException) {
            data=PerformanceData();storageAvailable=false
            Log.w("LANU_Perf","timings_invalid_reset")
        }
        loaded=true
    }
    private fun unavailable(output: java.io.FileOutputStream?) {
        output?.let {
            try { file.failWrite(it) }
            catch(_: IOException) { /* Statistics remain in memory. */ }
            catch(_: SecurityException) { /* Statistics remain in memory. */ }
        }
        storageAvailable=false
        Log.w("LANU_Perf","timings_memory_only")
    }
    private fun publish(): PerformanceSnapshot = PerformanceStats.snapshot(data,
        try { devices.runtimeHealth() } catch(_: RuntimeException) { null },storageAvailable)
        .also { mutable.value=it }
}
