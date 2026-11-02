package com.tapflow.core

/** How a row of the flattened block list is drawn. */
enum class RowKind {
    /** A simple block (tap, wait, ...). */
    Block,

    /** The top of a C-shaped repeat block ("repeat 5 times"). */
    RepeatHeader,

    /** The bottom cap of a repeat block. */
    RepeatEnd,
}

/**
 * One row of the block list as the editor draws it. Nested blocks are flattened so the
 * editor can lay them out in a single list, indented by [depth], with each repeat
 * drawn as a header row, its children and an end row.
 */
data class FlatRow(
    val kind: RowKind,
    val block: Block,
    val depth: Int,
    /** Ids of the repeat blocks this row is inside, outermost first. */
    val ancestors: List<String>,
) {
    val key: String get() = if (kind == RowKind.RepeatEnd) "${block.id}#end" else block.id
}

/** Where a block would land: inside [parentId] (null = top level) at [index]. */
data class Slot(val parentId: String?, val index: Int, val depth: Int)

/**
 * Pure functions for editing a block tree. Every function returns a new list and leaves
 * the input untouched.
 */
object BlockTree {

    fun find(blocks: List<Block>, id: String): Block? {
        for (b in blocks) {
            if (b.id == id) return b
            if (b is RepeatBlock) find(b.children, id)?.let { return it }
        }
        return null
    }

    /** Parent repeat id (null for the top level) and index of [id], or null if absent. */
    fun locate(blocks: List<Block>, id: String, parent: String? = null): Pair<String?, Int>? {
        blocks.forEachIndexed { i, b ->
            if (b.id == id) return parent to i
            if (b is RepeatBlock) locate(b.children, id, b.id)?.let { return it }
        }
        return null
    }

    /** Ids of [block] and everything nested inside it. */
    fun subtreeIds(block: Block): Set<String> = buildSet {
        fun walk(b: Block) {
            add(b.id)
            if (b is RepeatBlock) b.children.forEach(::walk)
        }
        walk(block)
    }

    fun count(blocks: List<Block>): Int =
        blocks.sumOf { 1 + if (it is RepeatBlock) count(it.children) else 0 }

    fun update(blocks: List<Block>, id: String, transform: (Block) -> Block): List<Block> =
        blocks.map { b ->
            when {
                b.id == id -> transform(b)
                b is RepeatBlock -> b.copy(children = update(b.children, id, transform))
                else -> b
            }
        }

    /** Removes [id] and returns the new list plus the removed block (null if not found). */
    fun remove(blocks: List<Block>, id: String): Pair<List<Block>, Block?> {
        var removed: Block? = null
        fun go(list: List<Block>): List<Block> = list.mapNotNull { b ->
            when {
                b.id == id -> {
                    removed = b
                    null
                }
                b is RepeatBlock && removed == null -> b.copy(children = go(b.children))
                else -> b
            }
        }
        val result = go(blocks)
        return result to removed
    }

    /**
     * Inserts [block] into [parentId] (null = top level) at [index], clamped to the valid
     * range. If [parentId] does not name a repeat block the list is returned unchanged.
     */
    fun insert(blocks: List<Block>, parentId: String?, index: Int, block: Block): List<Block> {
        if (parentId == null) {
            val i = index.coerceIn(0, blocks.size)
            return blocks.toMutableList().apply { add(i, block) }
        }
        return blocks.map { b ->
            when {
                b is RepeatBlock && b.id == parentId -> {
                    val i = index.coerceIn(0, b.children.size)
                    b.copy(children = b.children.toMutableList().apply { add(i, block) })
                }
                b is RepeatBlock -> b.copy(children = insert(b.children, parentId, index, block))
                else -> b
            }
        }
    }

    fun append(blocks: List<Block>, parentId: String?, block: Block): List<Block> =
        insert(blocks, parentId, Int.MAX_VALUE, block)

    /**
     * Moves [id] into [parentId] at [index] (the index counts positions after the block
     * has been taken out). Moving a repeat into itself or one of its descendants is
     * refused and returns [blocks] unchanged.
     */
    fun move(blocks: List<Block>, id: String, parentId: String?, index: Int): List<Block> {
        val block = find(blocks, id) ?: return blocks
        if (parentId != null && parentId in subtreeIds(block)) return blocks
        val (without, removed) = remove(blocks, id)
        if (removed == null) return blocks
        if (parentId != null && find(without, parentId) !is RepeatBlock) return blocks
        return insert(without, parentId, index, removed)
    }

    /** Inserts a deep copy (fresh ids) right after [id]. Returns the list and the copy's id. */
    fun duplicate(blocks: List<Block>, id: String): Pair<List<Block>, String?> {
        val block = find(blocks, id) ?: return blocks to null
        val (parent, index) = locate(blocks, id) ?: return blocks to null
        val copy = block.cloned()
        return insert(blocks, parent, index + 1, copy) to copy.id
    }

    fun flatten(blocks: List<Block>): List<FlatRow> = buildList {
        fun walk(list: List<Block>, depth: Int, ancestors: List<String>) {
            for (b in list) {
                if (b is RepeatBlock) {
                    add(FlatRow(RowKind.RepeatHeader, b, depth, ancestors))
                    walk(b.children, depth + 1, ancestors + b.id)
                    add(FlatRow(RowKind.RepeatEnd, b, depth, ancestors))
                } else {
                    add(FlatRow(RowKind.Block, b, depth, ancestors))
                }
            }
        }
        walk(blocks, 0, emptyList())
    }

    /**
     * Resolves a gap in a flattened list to a tree position. Gap `g` sits between
     * `rows[g - 1]` and `rows[g]`; `0` is the very top and `rows.size` the very bottom.
     * A gap just after a repeat header is inside that repeat, a gap just after a repeat's
     * end row is outside it, so every gap is a distinct position.
     */
    fun slotAt(rows: List<FlatRow>, gap: Int): Slot {
        val g = gap.coerceIn(0, rows.size)
        // Stack of open repeats with the number of direct children seen so far.
        val stack = ArrayDeque<Pair<String, Int>>()
        var topLevelIndex = 0
        fun bumpParent() {
            if (stack.isEmpty()) topLevelIndex++ else {
                val (pid, n) = stack.removeLast()
                stack.addLast(pid to n + 1)
            }
        }
        for (i in 0 until g) {
            val row = rows[i]
            when (row.kind) {
                RowKind.Block -> bumpParent()
                RowKind.RepeatHeader -> stack.addLast(row.block.id to 0)
                RowKind.RepeatEnd -> {
                    stack.removeLastOrNull()
                    bumpParent()
                }
            }
        }
        val top = stack.lastOrNull()
        return if (top == null) Slot(null, topLevelIndex, 0) else Slot(top.first, top.second, stack.size)
    }

    /**
     * Drag-and-drop: moves [id] to [gap], where the gap is measured in the flattened list
     * *without* the dragged block and its children (that's the list the user sees the
     * insertion marker in while dragging).
     */
    fun moveToGap(blocks: List<Block>, id: String, gap: Int): List<Block> {
        val (without, removed) = remove(blocks, id)
        if (removed == null) return blocks
        val slot = slotAt(flatten(without), gap)
        return insert(without, slot.parentId, slot.index, removed)
    }

    /** Points every reference to [targetId] at nothing, e.g. after deleting the target. */
    fun clearTarget(blocks: List<Block>, targetId: String): List<Block> = blocks.map { b ->
        when (b) {
            is TapBlock -> if (b.target == targetId) b.copy(target = null) else b
            is HoldBlock -> if (b.target == targetId) b.copy(target = null) else b
            is SwipeBlock -> b.copy(
                from = b.from.takeUnless { it == targetId },
                to = b.to.takeUnless { it == targetId },
            )
            is RepeatBlock -> b.copy(children = clearTarget(b.children, targetId))
            is WaitBlock, is RandomWaitBlock -> b
        }
    }
}
