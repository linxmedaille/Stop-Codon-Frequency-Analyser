package com.tapflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramBuilderTest {
    private val screen = ScreenSpace(1000, 2000)
    private val board = Board(
        targets = listOf(
            Target("p1", "P1", NPoint(0.5f, 0.25f)),
            Target("p2", "P2", NPoint(1f, 1f)),
        ),
    )

    @Test fun mapsNormalisedPointsToPixelsAndClampsEdges() {
        assertEquals(PxPoint(500f, 500f), screen.map(NPoint(0.5f, 0.25f)))
        assertEquals(PxPoint(999f, 1999f), screen.map(NPoint(1f, 1f)))
    }

    @Test fun singleNeedsAPoint() {
        val r = ProgramBuilder.single(SingleConfig(), screen)
        assertNull(r.program)
        assertEquals(1, r.errors.size)
    }

    @Test fun singleIntervalIncludesHold() {
        val r = ProgramBuilder.single(SingleConfig(board = board, intervalMs = 100, holdMs = 30, repeat = RepeatPolicy(RepeatMode.Times, times = 5)), screen)
        val loop = r.program!!.steps.single() as LoopStep
        assertEquals(5, loop.times)
        assertEquals(TapStep(PxPoint(500f, 500f), 30, "p1"), loop.body[0])
        assertEquals(WaitStep(70, 70, null), loop.body[1])
    }

    @Test fun durationModeLoopsForeverWithLimit() {
        val r = ProgramBuilder.single(SingleConfig(board = board, repeat = RepeatPolicy(RepeatMode.Duration, durationMs = 5_000)), screen)
        assertTrue((r.program!!.steps.single() as LoopStep).forever)
        assertEquals(5_000, r.program!!.timeLimitMs)
    }

    @Test fun multiUsesPerTargetDelays() {
        val cfg = MultiConfig(board = board, delays = mapOf("p2" to 2_000), defaultDelayMs = 300, cycleDelayMs = 50)
        val loop = ProgramBuilder.multi(cfg, screen).program!!.steps.single() as LoopStep
        assertEquals(
            listOf(300L, 2_000L, 50L),
            loop.body.filterIsInstance<WaitStep>().map { it.minMs },
        )
    }

    @Test fun chainCompilesNestedBlocks() {
        val cfg = ChainConfig(
            board = board,
            blocks = listOf(
                TapBlock("t", target = "p1", count = 2, gapMs = 100, holdMs = 10),
                WaitBlock("w", ms = 20_000),
                RepeatBlock("r", times = 3, children = listOf(SwipeBlock("s", from = "p1", to = "p2"))),
            ),
        )
        val r = ProgramBuilder.chain(cfg, screen)
        val steps = r.program!!.steps
        assertEquals(5, steps.size) // tap, gap, tap, wait, loop
        val loop = steps.last() as LoopStep
        assertEquals(3, loop.times)
        assertTrue(loop.body.single() is SwipeStep)
    }

    @Test fun chainWrapsRunsInOuterLoop() {
        val cfg = ChainConfig(board = board, blocks = listOf(TapBlock("t", target = "p1")), runs = RepeatPolicy(RepeatMode.Times, times = 4))
        val loop = ProgramBuilder.chain(cfg, screen).program!!.steps.single() as LoopStep
        assertEquals(RUNS_LOOP_ID, loop.sourceId)
        assertEquals(4, loop.times)
    }

    @Test fun chainReportsMissingTargetsPerBlock() {
        val cfg = ChainConfig(board = board, blocks = listOf(TapBlock("t", target = "nope"), RepeatBlock("r", times = 0, children = listOf(TapBlock("u", target = "p1"))), WaitBlock("w")))
        val r = ProgramBuilder.chain(cfg, screen)
        assertNull(r.program)
        assertEquals("t", r.errors.single().blockId)
        assertTrue(r.warnings.any { it.blockId == "r" }) // forever without a wait
        assertTrue(r.warnings.any { it.blockId == "w" }) // unreachable
    }

    @Test fun defaultChainIsTheBriefExample() {
        val r = ProgramBuilder.chain(ChainConfig(), screen)
        assertNotNull(r.program)
        assertTrue(r.errors.isEmpty())
        val steps = r.program!!.steps
        assertEquals(20_000, (steps[1] as WaitStep).minMs)
    }
}
