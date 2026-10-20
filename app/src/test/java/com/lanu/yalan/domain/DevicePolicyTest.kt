package com.lanu.yalan.domain

import org.junit.Assert.*
import org.junit.Test

class DevicePolicyTest {
    private val gib = 1024L * 1024 * 1024
    @Test fun modestMemoryLimitsParallelInference() {
        assertEquals(2, DevicePolicy.inferenceThreads(8, 4 * gib))
        assertEquals(4, DevicePolicy.inferenceThreads(8, 8 * gib))
    }
    @Test fun neverCreatesMoreWorkersThanAvailableCores() {
        assertEquals(1, DevicePolicy.inferenceThreads(1, 8 * gib))
        assertEquals(2, DevicePolicy.inferenceThreads(2, 8 * gib))
        assertEquals(1, DevicePolicy.inferenceThreads(0, 0))
    }
    @Test fun memoryClassAccountsForAndroidReservedMemory() {
        assertEquals(8, DevicePolicy.memoryClassGb(15 * gib / 2))
        assertEquals(4, DevicePolicy.memoryClassGb(7 * gib / 2))
        assertEquals(0, DevicePolicy.memoryClassGb(0))
    }
}
