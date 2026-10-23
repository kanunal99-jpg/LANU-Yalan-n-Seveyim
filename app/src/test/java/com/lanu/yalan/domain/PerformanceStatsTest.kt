package com.lanu.yalan.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PerformanceStatsTest {
    @Test fun successesHaveNearestRankPercentilesAndFailuresStaySeparate() {
        val samples=(1..100).map { TimingSample(PlayMode.FAST,it.toLong()) }+
            listOf(TimingSample(PlayMode.FAST,30_000,TimingOutcome.TIMEOUT),
                TimingSample(PlayMode.FAST,1,TimingOutcome.ERROR),TimingSample(PlayMode.FAST,1,TimingOutcome.CANCELLED))
        val summary=PerformanceStats.summarize(samples)
        assertEquals(100,summary.successCount)
        assertEquals(50L,summary.p50Ms)
        assertEquals(95L,summary.p95Ms)
        assertEquals(100L,summary.lastMs)
        assertEquals(1,summary.timeoutCount)
        assertEquals(1,summary.errorCount)
        assertEquals(1,summary.cancelledCount)
    }
    @Test fun openingsAndModesCannotInflateReplyPerformance() {
        val data=PerformanceData(samples=listOf(TimingSample(PlayMode.FAST,450),
            TimingSample(PlayMode.LOCAL_AI,20_000),TimingSample(PlayMode.LOCAL_AI,20,phase=TimingPhase.OPENING)))
        val summary=PerformanceStats.snapshot(data)
        assertEquals(450L,summary.fast.p95Ms)
        assertEquals(20_000L,summary.ai.p95Ms)
        assertEquals(20L,summary.opening.p95Ms)
        assertEquals(1,summary.ai.successCount)
    }
    @Test fun noSuccessMeansNoInventedFastTiming() {
        val result=PerformanceStats.summarize(listOf(TimingSample(PlayMode.LOCAL_AI,30_000,TimingOutcome.TIMEOUT)))
        assertEquals(0,result.successCount)
        assertNull(result.p50Ms)
        assertNull(result.p95Ms)
        assertEquals(1,result.timeoutCount)
    }
    @Test fun rollingHistoryIsBoundedAndContainsOnlyMetrics() {
        var data=PerformanceData()
        for(ms in 1..120) data=PerformanceStats.record(data,TimingSample(PlayMode.FAST,ms.toLong()))
        assertEquals(100,data.samples.size)
        assertEquals(21L,data.samples.first().elapsedMs)
        val restored=Json.decodeFromString<PerformanceData>(Json.encodeToString(data))
        assertEquals(data,PerformanceStats.validated(restored))
    }
    @Test fun invalidImportedMetricsAreRejected() {
        listOf(PerformanceData(schema=2),PerformanceData(samples=listOf(TimingSample(PlayMode.FAST,-1))),
            PerformanceData(samples=List(101) { TimingSample(PlayMode.FAST,1) })).forEach { data ->
            assertThrows(IllegalArgumentException::class.java) { PerformanceStats.validated(data) }
        }
    }
}
