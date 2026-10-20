package com.lanu.yalan.domain

/** Resource limits depend on hardware, never manufacturer or installed app stores. */
object DevicePolicy {
    private const val GIB = 1024L * 1024 * 1024
    fun inferenceThreads(cpuCores: Int, totalMemoryBytes: Long): Int {
        val limit = if (totalMemoryBytes < 6 * GIB) 2 else 4
        return cpuCores.coerceAtLeast(1).coerceAtMost(limit)
    }
    fun memoryClassGb(totalMemoryBytes: Long): Int =
        if (totalMemoryBytes <= 0) 0 else ((totalMemoryBytes + GIB - 1) / GIB).toInt()
}
