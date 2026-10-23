package com.lanu.yalan.domain

import kotlinx.serialization.Serializable
import kotlin.math.ceil

@Serializable enum class TimingOutcome { SUCCESS, TIMEOUT, ERROR, CANCELLED }
@Serializable enum class TimingPhase { OPENING, REPLY }

/** App-local engine + encrypted progress-save time, not a display-frame measurement. */
@Serializable data class TimingSample(
    val mode: PlayMode,
    val elapsedMs: Long,
    val outcome: TimingOutcome = TimingOutcome.SUCCESS,
    val phase: TimingPhase = TimingPhase.REPLY
)
@Serializable data class PerformanceData(val schema: Int = 1, val samples: List<TimingSample> = emptyList())

data class TimingSummary(
    val successCount: Int = 0,
    val p50Ms: Long? = null,
    val p95Ms: Long? = null,
    val timeoutCount: Int = 0,
    val errorCount: Int = 0,
    val cancelledCount: Int = 0,
    val lastMs: Long? = null
)
data class RuntimeHealth(
    val availableMemoryMb: Long,
    val totalMemoryMb: Long,
    val lowMemory: Boolean,
    val thermalStatus: Int?,
    val powerSave: Boolean,
    val batteryPercent: Int?
) {
    val summary: String get() = "$availableMemoryMb MB kullanılabilir RAM · $thermalLabel"+
        (batteryPercent?.let { " · Pil %$it" } ?: "")+(if(powerSave) " · Güç tasarrufu açık" else "")
    val thermalLabel: String get() = when(thermalStatus) {
        0 -> "Normal"; 1 -> "Hafif sıcak"; 2 -> "Isınıyor"; 3 -> "Yüksek sıcaklık"
        4,5,6 -> "Kritik sıcaklık"; else -> "Ölçüm desteklenmiyor"
    }
}
data class PerformanceSnapshot(
    val fast: TimingSummary = TimingSummary(),
    val ai: TimingSummary = TimingSummary(),
    val opening: TimingSummary = TimingSummary(),
    val health: RuntimeHealth? = null,
    val storageAvailable: Boolean = true
)

/** Bounded statistics; failed/cancelled requests never improve the success percentiles. */
object PerformanceStats {
    const val MAX_SAMPLES = 100
    const val MAX_ELAPSED_MS = 600_000L
    fun validated(data: PerformanceData): PerformanceData {
        require(data.schema==1 && data.samples.size<=MAX_SAMPLES)
        require(data.samples.all { it.elapsedMs in 0..MAX_ELAPSED_MS })
        return data
    }
    fun record(data: PerformanceData, sample: TimingSample): PerformanceData {
        validated(data)
        require(sample.elapsedMs in 0..MAX_ELAPSED_MS)
        return data.copy(samples=(data.samples+sample).takeLast(MAX_SAMPLES))
    }
    fun summarize(samples: List<TimingSample>): TimingSummary {
        val success=samples.filter { it.outcome==TimingOutcome.SUCCESS }
        val sorted=success.map { it.elapsedMs }.sorted()
        fun percentile(p: Double): Long? = if(sorted.isEmpty()) null else sorted[(ceil(sorted.size*p).toInt()-1).coerceAtLeast(0)]
        return TimingSummary(success.size,percentile(.5),percentile(.95),
            samples.count { it.outcome==TimingOutcome.TIMEOUT },samples.count { it.outcome==TimingOutcome.ERROR },
            samples.count { it.outcome==TimingOutcome.CANCELLED },success.lastOrNull()?.elapsedMs)
    }
    fun snapshot(data: PerformanceData, health: RuntimeHealth? = null, storageAvailable: Boolean = true): PerformanceSnapshot {
        validated(data)
        val replies=data.samples.filter { it.phase==TimingPhase.REPLY }
        return PerformanceSnapshot(summarize(replies.filter { it.mode==PlayMode.FAST }),
            summarize(replies.filter { it.mode==PlayMode.LOCAL_AI }),
            summarize(data.samples.filter { it.phase==TimingPhase.OPENING }),health,storageAvailable)
    }
}
