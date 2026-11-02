package com.tapflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class BlockTreeTest {
    private val tree = listOf(
        TapBlock("a"),
        RepeatBlock("r", times = 2, children = listOf(TapBlock("b"), WaitBlock("c"))),
        WaitBlock("d"),
    )

    private fun ids(rows: List<FlatRow>) = rows.map { it.key }

    @Test fun flattenProducesHeaderChildrenAndEnd() {
        val rows = BlockTree.flatten(tree)
        assertEquals(listOf("a", "r", "b", "c", "r#end", "d"), ids(rows))
        assertEquals(listOf(0, 0, 1, 1, 0, 0), rows.map { it.depth })
        assertEquals(listOf("r"), rows[2].ancestors)
    }

    @Test fun slotsResolveInsideAndOutsideRepeats() {
        val rows = BlockTree.flatten(tree)
        assertEquals(Slot(null, 0, 0), BlockTree.slotAt(rows, 0))
        assertEquals(Slot(null, 1, 0), BlockTree.slotAt(rows, 1)) // before the repeat
        assertEquals(Slot("r", 0, 1), BlockTree.slotAt(rows, 2)) // first inside
        assertEquals(Slot("r", 2, 1), BlockTree.slotAt(rows, 4)) // last inside, before end
        assertEquals(Slot(null, 2, 0), BlockTree.slotAt(rows, 5)) // just after the repeat
        assertEquals(Slot(null, 3, 0), BlockTree.slotAt(rows, 6)) // bottom
    }

    @Test fun moveToGapMovesIntoRepeat() {
        // Drag "d" to the gap just inside the repeat. Without "d" the list is a, r, b, c, r#end.
        val moved = BlockTree.moveToGap(tree, "d", 2)
        assertEquals(listOf("a", "r", "d", "b", "c", "r#end"), ids(BlockTree.flatten(moved)))
    }

    @Test fun moveToGapMovesOutOfRepeat() {
        val moved = BlockTree.moveToGap(tree, "b", 0)
        assertEquals(listOf("b", "a", "r", "c", "r#end", "d"), ids(BlockTree.flatten(moved)))
    }

    @Test fun moveWholeRepeatKeepsChildren() {
        val moved = BlockTree.moveToGap(tree, "r", 2) // without r: a, d -> gap 2 is the bottom
        assertEquals(listOf("a", "d", "r", "b", "c", "r#end"), ids(BlockTree.flatten(moved)))
    }

    @Test fun cannotMoveRepeatIntoItself() {
        val nested = listOf(RepeatBlock("outer", children = listOf(RepeatBlock("inner"))))
        assertSame(nested, BlockTree.move(nested, "outer", "inner", 0))
        assertSame(nested, BlockTree.move(nested, "outer", "outer", 0))
    }

    @Test fun insertRemoveUpdate() {
        val inserted = BlockTree.insert(tree, "r", 1, HoldBlock("h"))
        assertEquals(listOf("a", "r", "b", "h", "c", "r#end", "d"), ids(BlockTree.flatten(inserted)))
        val (removed, block) = BlockTree.remove(inserted, "h")
        assertEquals(tree, removed)
        assertEquals("h", block?.id)
        val updated = BlockTree.update(tree, "c") { (it as WaitBlock).copy(ms = 5) }
        assertEquals(5, (BlockTree.find(updated, "c") as WaitBlock).ms)
        assertNull(BlockTree.remove(tree, "zzz").second)
    }

    @Test fun duplicateUsesFreshIdsAndSitsAfterOriginal() {
        val (dup, newId) = BlockTree.duplicate(tree, "r")
        val rows = BlockTree.flatten(dup)
        assertEquals(10, rows.size)
        assertEquals(newId, rows[5].block.id)
        val allIds = BlockTree.flatten(dup).filter { it.kind != RowKind.RepeatEnd }.map { it.block.id }
        assertEquals(allIds.size, allIds.toSet().size)
    }

    @Test fun clearTargetUnsetsReferences() {
        val blocks = listOf(TapBlock("a", target = "p1"), SwipeBlock("s", from = "p1", to = "p2"))
        val cleared = BlockTree.clearTarget(blocks, "p1")
        assertNull((cleared[0] as TapBlock).target)
        assertNull((cleared[1] as SwipeBlock).from)
        assertEquals("p2", (cleared[1] as SwipeBlock).to)
    }

    @Test fun locateFindsParentAndIndex() {
        assertEquals("r" to 1, BlockTree.locate(tree, "c"))
        assertEquals(null to 2, BlockTree.locate(tree, "d"))
        assertEquals(5, BlockTree.count(tree))
    }
}
