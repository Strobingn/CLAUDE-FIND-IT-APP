package com.example.data

import kotlin.math.max
import kotlin.math.min

/**
 * Device-adaptive memory limits for large LiDAR workloads.
 *
 * Android owns the process heap ceiling. With android:largeHeap enabled,
 * Runtime.maxMemory() reports the larger per-process limit granted by the device.
 * CHATGPTV2.0 uses nearly all non-reserved heap for the two persistent LRU caches while
 * preserving explicit headroom for Compose, bitmaps, LAZ decoding, OpenGL, and analysis arrays.
 */
object AppMemoryBudget {
    private const val MIB = 1024L * 1024L
    private const val GIB = 1024L * MIB

    /** Actual Java heap ceiling for this process. */
    val maxHeapBytes: Long
        get() = Runtime.getRuntime().maxMemory().coerceAtLeast(128L * MIB)

    /** Reserved for transient decoding, UI bitmaps, native/GPU allocations, and network buffers. */
    val reservedHeadroomBytes: Long
        get() = max(128L * MIB, (maxHeapBytes * 0.18).toLong())
            .coerceAtMost((maxHeapBytes * 0.30).toLong())

    /** Budget available to the decoded-terrain and derived-layer caches together. */
    val persistentCacheBudgetBytes: Long
        get() = (maxHeapBytes - reservedHeadroomBytes).coerceAtLeast(96L * MIB)

    /** Decoded DEM cache gets the larger share because reopening LAZ is the most expensive path. */
    fun terrainMemoryCacheBytes(): Long = shareOfPersistentBudget(
        fraction = 0.62,
        minimum = 96L * MIB,
        maximum = 3L * GIB,
    )

    /** Derived terrain layers retain the remainder for rapid analysis-mode switching. */
    fun derivedLayerMemoryCacheBytes(): Long = shareOfPersistentBudget(
        fraction = 0.38,
        minimum = 64L * MIB,
        maximum = 2L * GIB,
    )

    fun describe(): String = buildString {
        append("Heap ")
        append(formatBytes(maxHeapBytes))
        append(" · decoded cache ")
        append(formatBytes(terrainMemoryCacheBytes()))
        append(" · derived cache ")
        append(formatBytes(derivedLayerMemoryCacheBytes()))
        append(" · reserved ")
        append(formatBytes(reservedHeadroomBytes))
    }

    private fun shareOfPersistentBudget(fraction: Double, minimum: Long, maximum: Long): Long {
        val available = persistentCacheBudgetBytes
        val requested = (available * fraction).toLong()
        return min(maximum, max(minimum.coerceAtMost(available), requested.coerceAtMost(available)))
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= GIB -> String.format("%.2f GiB", bytes.toDouble() / GIB)
        else -> String.format("%.0f MiB", bytes.toDouble() / MIB)
    }
}
