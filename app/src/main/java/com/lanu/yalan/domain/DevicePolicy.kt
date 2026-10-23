package com.lanu.yalan.domain

/** Resource limits depend on hardware, never manufacturer or installed app stores. */
object DevicePolicy {
    private const val GIB = 1024L * 1024 * 1024
    fun inferenceThreads(cpuCores: Int, totalMemoryBytes: Long, thermalStatus: Int? = null, powerSave: Boolean = false): Int {
        val hardwareLimit = if (totalMemoryBytes < 6 * GIB) 2 else 4
        // Android thermal levels: 2 moderate, 3 severe, 4 critical. No vendor-specific branches.
        val thermalLimit = when { (thermalStatus ?: 0)>=3 -> 1; (thermalStatus ?: 0)>=2 || powerSave -> 2; else -> 4 }
        val limit = minOf(hardwareLimit,thermalLimit)
        return cpuCores.coerceAtLeast(1).coerceAtMost(limit)
    }
    fun shouldDeferInference(thermalStatus: Int?): Boolean = (thermalStatus ?: 0)>=4
    fun memoryClassGb(totalMemoryBytes: Long): Int =
        if (totalMemoryBytes <= 0) 0 else ((totalMemoryBytes + GIB - 1) / GIB).toInt()
}
