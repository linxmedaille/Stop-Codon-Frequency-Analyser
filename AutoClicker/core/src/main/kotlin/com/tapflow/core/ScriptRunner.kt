package com.tapflow.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Performs gestures on the real screen (the accessibility service) or in tests. */
interface GesturePerformer {
    /** Returns false if the system rejected or cancelled the gesture. */
    suspend fun tap(at: PxPoint, holdMs: Long): Boolean

    suspend fun swipe(from: PxPoint, to: PxPoint, durationMs: Long): Boolean
}

enum class FinishReason { Completed, Stopped, TimeLimit }

sealed interface RunEvent {
    /** A gesture is about to be performed. */
    data class Gesture(val sourceId: String?, val at: PxPoint, val to: PxPoint? = null) : RunEvent

    /** A wait started; [ms] is the actual (possibly randomised) length. */
    data class Waiting(val sourceId: String?, val ms: Long) : RunEvent

    /** Iteration [iteration] (1-based) of the loop [sourceId] started; [times] 0 = forever. */
    data class Loop(val sourceId: String?, val iteration: Int, val times: Int) : RunEvent

    data class Finished(val reason: FinishReason, val stats: RunStats) : RunEvent
}

data class RunStats(val gestures: Long, val rejected: Long, val activeMs: Long)

/**
 * Executes a [Program]. Pausing (via [paused]) freezes waits mid-way and is excluded from
 * the time limit. Cancel the calling coroutine to stop.
 */
class ScriptRunner(
    private val performer: GesturePerformer,
    private val paused: StateFlow<Boolean>,
    private val random: Random = Random.Default,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private class TimeLimitReached : Exception()

    private var gestures = 0L
    private var rejected = 0L
    private var pausedMs = 0L
    private lateinit var started: TimeMark
    private var limitMs: Long? = null
    private lateinit var program: Program
    private lateinit var emit: (RunEvent) -> Unit

    private fun activeMs(): Long = started.elapsedNow().inWholeMilliseconds - pausedMs

    /** Runs [program] to completion (or the time limit) and returns why it ended. */
    suspend fun run(program: Program, onEvent: (RunEvent) -> Unit = {}): FinishReason {
        this.program = program
        this.emit = onEvent
        gestures = 0; rejected = 0; pausedMs = 0
        limitMs = program.timeLimitMs
        started = timeSource.markNow()
        val reason = try {
            runSteps(program.steps)
            FinishReason.Completed
        } catch (_: TimeLimitReached) {
            FinishReason.TimeLimit
        } catch (e: CancellationException) {
            onEvent(RunEvent.Finished(FinishReason.Stopped, stats()))
            throw e
        }
        onEvent(RunEvent.Finished(reason, stats()))
        return reason
    }

    fun stats() = RunStats(gestures, rejected, if (::started.isInitialized) activeMs() else 0)

    private suspend fun runSteps(steps: List<Step>) {
        for (step in steps) {
            gate()
            when (step) {
                is TapStep -> {
                    val at = jitter(step.at)
                    emit(RunEvent.Gesture(step.sourceId, at))
                    count(performer.tap(at, step.holdMs))
                }
                is SwipeStep -> {
                    val from = jitter(step.from)
                    val to = jitter(step.to)
                    emit(RunEvent.Gesture(step.sourceId, from, to))
                    count(performer.swipe(from, to, step.durationMs))
                }
                is WaitStep -> {
                    val ms = waitLength(step)
                    emit(RunEvent.Waiting(step.sourceId, ms))
                    sleep(ms)
                }
                is LoopStep -> {
                    var i = 0
                    while (step.forever || i < step.times) {
                        i++
                        gate()
                        emit(RunEvent.Loop(step.sourceId, i, step.times))
                        val before = gestures
                        val t0 = activeMs()
                        runSteps(step.body)
                        // A loop body that neither taps nor waits would spin the CPU.
                        if (gestures == before && activeMs() == t0) delay(16) else yield()
                    }
                }
            }
        }
    }

    private fun count(ok: Boolean) {
        gestures++
        if (!ok) rejected++
    }

    /** Waits while paused; throws once the time limit is used up. */
    private suspend fun gate() {
        if (paused.value) {
            val mark = timeSource.markNow()
            paused.first { !it }
            pausedMs += mark.elapsedNow().inWholeMilliseconds
        }
        val limit = limitMs
        if (limit != null && activeMs() >= limit) throw TimeLimitReached()
    }

    /** Sleeps [ms] of active time: pausing freezes the countdown. */
    private suspend fun sleep(ms: Long) {
        var remaining = ms
        limitMs?.let { remaining = minOf(remaining, (it - activeMs()).coerceAtLeast(0)) }
        while (remaining > 0) {
            gate()
            val mark = timeSource.markNow()
            val pausedEarly = withTimeoutOrNull(remaining) { paused.first { it } } != null
            remaining -= mark.elapsedNow().inWholeMilliseconds
            if (!pausedEarly) break
        }
        gate()
    }

    private fun waitLength(step: WaitStep): Long {
        val base = if (step.maxMs > step.minMs) random.nextLong(step.minMs, step.maxMs + 1) else step.minMs
        val h = program.humanize
        if (!h.enabled || h.timingPct <= 0 || base == 0L) return base
        val f = 1.0 + (random.nextDouble() * 2 - 1) * h.timingPct / 100.0
        return (base * f).roundToLong().coerceAtLeast(0)
    }

    private fun jitter(p: PxPoint): PxPoint {
        val h = program.humanize
        if (!h.enabled || h.offsetPx <= 0) return p
        // Uniform inside a disc.
        val r = h.offsetPx * sqrt(random.nextDouble())
        val a = random.nextDouble() * 2 * PI
        val s = program.screen
        return PxPoint(
            x = (p.x + r * cos(a)).toFloat().coerceIn(0f, (s.width - 1).coerceAtLeast(0).toFloat()),
            y = (p.y + r * sin(a)).toFloat().coerceIn(0f, (s.height - 1).coerceAtLeast(0).toFloat()),
        )
    }
}
