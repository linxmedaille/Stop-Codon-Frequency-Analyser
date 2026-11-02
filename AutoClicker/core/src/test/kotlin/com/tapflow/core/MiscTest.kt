package com.tapflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MiscTest {
    @Test fun formatsDurations() {
        assertEquals("40 ms", formatDuration(40))
        assertEquals("1.5 s", formatDuration(1_500))
        assertEquals("2 s", formatDuration(2_000))
        assertEquals("20 s", formatDuration(20_000))
        assertEquals("2 min 5 s", formatDuration(125_000))
        assertEquals("1 h 2 min", formatDuration(3_720_000))
        assertEquals("1.5s", formatDurationShort(1_500))
        assertEquals("1h30m", formatDurationShort(5_400_000))
        assertEquals("1,234,567", formatCount(1_234_567))
    }

    @Test fun estimatesChains() {
        val blocks = ChainConfig().blocks
        // 20 + 20000 + 20 + 5 * (20 + 1000)
        assertEquals(25_140, Estimate.durationMs(blocks))
        assertEquals(7, Estimate.gestures(blocks))
        assertNull(Estimate.durationMs(listOf(RepeatBlock("r", times = 0, children = listOf(WaitBlock("w"))))))
    }

    @Test fun workspaceRoundTripsAndSurvivesGarbage() {
        val ws = Workspace(chain = ChainConfig(runs = RepeatPolicy(RepeatMode.Forever)), lastMode = Mode.Chain)
        assertEquals(ws, WorkspaceCodec.decode(WorkspaceCodec.encode(ws)))
        assertEquals(Workspace(), WorkspaceCodec.decode("{not json"))
        assertEquals(Workspace(), WorkspaceCodec.decode(null))
    }

    @Test fun boardNamesTargetsSequentially() {
        var b = Board()
        b = b.addTarget(NPoint(0f, 0f)).first
        b = b.addTarget(NPoint(2f, -1f)).first
        assertEquals(listOf("P1", "P2"), b.targets.map { it.name })
        assertEquals(NPoint(1f, 0f), b.targets[1].point)
        b = b.removeTarget(b.targets[0].id)
        assertEquals("P1", b.nextName())
    }
}
