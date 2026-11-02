package com.tapflow.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptRunnerTest {
    private val screen = ScreenSpace(1080, 2400)

    private class Recorder(private val clock: () -> Long) : GesturePerformer {
        val taps = mutableListOf<Pair<Long, PxPoint>>()
        override suspend fun tap(at: PxPoint, holdMs: Long): Boolean {
            taps += clock() to at
            delay(holdMs)
            return true
        }
        override suspend fun swipe(from: PxPoint, to: PxPoint, durationMs: Long): Boolean {
            taps += clock() to from
            delay(durationMs)
            return true
        }
    }

    @Test fun briefExampleTiming() = runTest {
        val rec = Recorder { testScheduler.currentTime }
        val program = ProgramBuilder.chain(ChainConfig(), screen).program!!
        val runner = ScriptRunner(rec, MutableStateFlow(false), Random(1), testScheduler.timeSource)
        val reason = runner.run(program)
        assertEquals(FinishReason.Completed, reason)
        // P1, wait 20 s, P2, then repeat 5 x (P1, wait 1 s) => 7 taps.
        assertEquals(7, rec.taps.size)
        assertEquals(0L, rec.taps[0].first)
        assertEquals(20_020L, rec.taps[1].first) // 20 ms hold + 20 s wait
        assertEquals(rec.taps[0].second, rec.taps[2].second)
    }

    @Test fun timeLimitStopsForeverLoop() = runTest {
        val rec = Recorder { testScheduler.currentTime }
        val cfg = SingleConfig(
            board = Board(targets = listOf(Target("p", "P1", NPoint(0.5f, 0.5f)))),
            intervalMs = 100, holdMs = 10,
            repeat = RepeatPolicy(RepeatMode.Duration, durationMs = 1_000),
        )
        val program = ProgramBuilder.single(cfg, screen).program!!
        val runner = ScriptRunner(rec, MutableStateFlow(false), Random(1), testScheduler.timeSource)
        assertEquals(FinishReason.TimeLimit, runner.run(program))
        assertEquals(10, rec.taps.size)
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test fun pauseFreezesWaitsAndTimeLimit() = runTest {
        val rec = Recorder { testScheduler.currentTime }
        val paused = MutableStateFlow(false)
        val cfg = ChainConfig(
            board = Board(targets = listOf(Target("p", "P1", NPoint(0.5f, 0.5f)))),
            blocks = listOf(TapBlock("a", target = "p", holdMs = 1), WaitBlock("w", ms = 1_000), TapBlock("b", target = "p", holdMs = 1)),
        )
        val program = ProgramBuilder.chain(cfg, screen).program!!
        val runner = ScriptRunner(rec, paused, Random(1), testScheduler.timeSource)
        val job = launch { runner.run(program) }
        advanceTimeBy(501) // 1 ms tap + 500 ms of the wait
        paused.value = true
        advanceTimeBy(10_000)
        assertEquals(1, rec.taps.size)
        paused.value = false
        advanceTimeBy(499)
        runCurrent()
        assertEquals(1, rec.taps.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, rec.taps.size)
        job.join()
        assertTrue(runner.stats().activeMs in 1_000..1_010)
    }

    @Test fun cancellingReportsStopped() = runTest {
        val rec = Recorder { testScheduler.currentTime }
        val cfg = SingleConfig(board = Board(targets = listOf(Target("p", "P1", NPoint(0.5f, 0.5f)))))
        val program = ProgramBuilder.single(cfg, screen).program!!
        val events = mutableListOf<RunEvent>()
        val job = launch { ScriptRunner(rec, MutableStateFlow(false), Random(1), testScheduler.timeSource).run(program) { events += it } }
        advanceTimeBy(1_000)
        job.cancel()
        job.join()
        val finished = events.last() as RunEvent.Finished
        assertEquals(FinishReason.Stopped, finished.reason)
        assertTrue(finished.stats.gestures >= 9)
    }

    @Test fun emptyForeverLoopDoesNotSpin() = runTest {
        val program = Program(steps = listOf(LoopStep(0, emptyList(), "x")), screen = screen, timeLimitMs = 100)
        val rec = Recorder { testScheduler.currentTime }
        val reason = ScriptRunner(rec, MutableStateFlow(false), Random(1), testScheduler.timeSource).run(program)
        assertEquals(FinishReason.TimeLimit, reason)
    }

    @Test fun humanizeStaysNearTarget() = runTest {
        val rec = Recorder { testScheduler.currentTime }
        val cfg = SingleConfig(
            board = Board(targets = listOf(Target("p", "P1", NPoint(0.5f, 0.5f)))),
            repeat = RepeatPolicy(RepeatMode.Times, times = 200),
            humanize = Humanize(enabled = true, offsetPx = 10, timingPct = 20),
        )
        val program = ProgramBuilder.single(cfg, screen).program!!
        ScriptRunner(rec, MutableStateFlow(false), Random(7), testScheduler.timeSource).run(program)
        val c = screen.map(NPoint(0.5f, 0.5f))
        assertEquals(200, rec.taps.size)
        assertTrue(rec.taps.all { (_, p) -> kotlin.math.hypot((p.x - c.x).toDouble(), (p.y - c.y).toDouble()) <= 10.0001 })
        assertTrue(rec.taps.map { it.second }.toSet().size > 150)
    }

    @Test fun loopEventsCountIterations() = runTest {
        val rec = Recorder { testScheduler.currentTime }
        val program = ProgramBuilder.chain(ChainConfig(), screen).program!!
        val loops = mutableListOf<RunEvent.Loop>()
        ScriptRunner(rec, MutableStateFlow(false), Random(1), testScheduler.timeSource).run(program) { if (it is RunEvent.Loop) loops += it }
        assertEquals(listOf(1, 2, 3, 4, 5), loops.map { it.iteration })
        assertTrue(loops.all { it.sourceId == "b4" && it.times == 5 })
    }
}
