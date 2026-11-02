package com.tapflow.core

import kotlin.math.roundToLong

/** Rough timing figures for the editors. */
object Estimate {

    /** Length of one pass through [blocks], or null if it contains a forever loop. */
    fun durationMs(blocks: List<Block>): Long? {
        var total = 0L
        for (b in blocks) {
            total += when (b) {
                is TapBlock -> {
                    val n = b.count.coerceIn(1, 50)
                    n * b.holdMs + (n - 1) * b.gapMs
                }
                is HoldBlock -> b.durationMs
                is SwipeBlock -> b.durationMs
                is WaitBlock -> b.ms
                is RandomWaitBlock -> (b.minMs + b.maxMs) / 2
                is RepeatBlock -> {
                    if (b.forever && b.children.isNotEmpty()) return null
                    val inner = durationMs(b.children) ?: return null
                    inner * b.times.coerceAtLeast(0)
                }
            }
        }
        return total
    }

    /** Number of taps/holds/swipes in one pass, or null if infinite. */
    fun gestures(blocks: List<Block>): Long? {
        var total = 0L
        for (b in blocks) {
            total += when (b) {
                is TapBlock -> b.count.coerceIn(1, 50).toLong()
                is HoldBlock, is SwipeBlock -> 1L
                is WaitBlock, is RandomWaitBlock -> 0L
                is RepeatBlock -> {
                    val inner = gestures(b.children) ?: return null
                    if (b.forever && inner > 0) return null
                    inner * b.times.coerceAtLeast(0)
                }
            }
        }
        return total
    }

    /** Taps per second for the single-point clicker. */
    fun tapsPerSecond(intervalMs: Long): Double = 1000.0 / intervalMs.coerceAtLeast(1)

    /** Total running time of a single/multi routine, or null when it runs until stopped. */
    fun totalMs(cycleMs: Long, policy: RepeatPolicy): Long? = when (policy.mode) {
        RepeatMode.Forever -> null
        RepeatMode.Times -> cycleMs * policy.times.coerceAtLeast(1)
        RepeatMode.Duration -> policy.durationMs
    }
}

/** "40 ms", "1.5 s", "20 s", "2 min 5 s", "1 h 2 min". */
fun formatDuration(ms: Long): String {
    if (ms < 1_000) return "$ms ms"
    if (ms < 10_000) {
        val tenths = (ms / 100.0).roundToLong()
        return if (tenths % 10 == 0L) "${tenths / 10} s" else "${tenths / 10}.${tenths % 10} s"
    }
    val totalSec = (ms / 1000.0).roundToLong()
    if (totalSec < 60) return "$totalSec s"
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> if (m > 0) "$h h $m min" else "$h h"
        s > 0 -> "$m min $s s"
        else -> "$m min"
    }
}

/** Compact duration for value pills: "40ms", "1.5s", "20s", "2m", "1h30m". */
fun formatDurationShort(ms: Long): String {
    if (ms < 1_000) return "${ms}ms"
    if (ms < 10_000) {
        val tenths = (ms / 100.0).roundToLong()
        return if (tenths % 10 == 0L) "${tenths / 10}s" else "${tenths / 10}.${tenths % 10}s"
    }
    val totalSec = (ms / 1000.0).roundToLong()
    if (totalSec < 60) return "${totalSec}s"
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> if (m > 0) "${h}h${m}m" else "${h}h"
        s > 0 -> "${m}m${s}s"
        else -> "${m}m"
    }
}

/** "1,234" style grouping without depending on a locale. */
fun formatCount(n: Long): String {
    val s = kotlin.math.abs(n).toString()
    val grouped = s.reversed().chunked(3).joinToString(",").reversed()
    return if (n < 0) "-$grouped" else grouped
}
