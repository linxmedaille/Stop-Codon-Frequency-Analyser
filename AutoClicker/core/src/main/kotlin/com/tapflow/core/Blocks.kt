package com.tapflow.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One block of a chain, Scratch style. Blocks reference targets by id so moving a point on
 * the screenshot updates every block that uses it.
 */
@Serializable
sealed interface Block {
    val id: String

    /** A copy with a fresh id (children too). */
    fun cloned(): Block
}

@Serializable
@SerialName("tap")
data class TapBlock(
    override val id: String,
    val target: String? = null,
    val holdMs: Long = 20,
    /** Number of taps in a row (2 = double tap). */
    val count: Int = 1,
    /** Pause between the taps when [count] > 1. */
    val gapMs: Long = 90,
) : Block {
    override fun cloned() = copy(id = newId())
}

@Serializable
@SerialName("hold")
data class HoldBlock(
    override val id: String,
    val target: String? = null,
    val durationMs: Long = 800,
) : Block {
    override fun cloned() = copy(id = newId())
}

@Serializable
@SerialName("swipe")
data class SwipeBlock(
    override val id: String,
    val from: String? = null,
    val to: String? = null,
    val durationMs: Long = 300,
) : Block {
    override fun cloned() = copy(id = newId())
}

@Serializable
@SerialName("wait")
data class WaitBlock(
    override val id: String,
    val ms: Long = 1_000,
) : Block {
    override fun cloned() = copy(id = newId())
}

@Serializable
@SerialName("wait_random")
data class RandomWaitBlock(
    override val id: String,
    val minMs: Long = 500,
    val maxMs: Long = 1_500,
) : Block {
    override fun cloned() = copy(id = newId())
}

@Serializable
@SerialName("repeat")
data class RepeatBlock(
    override val id: String,
    /** How many times to run [children]; 0 means forever. */
    val times: Int = 3,
    val children: List<Block> = emptyList(),
) : Block {
    val forever: Boolean get() = times <= 0
    override fun cloned() = copy(id = newId(), children = children.map { it.cloned() })
}

/** The kinds a user can pick from the palette, in palette order. */
enum class BlockKind { Tap, Hold, Swipe, Wait, RandomWait, Repeat }

val Block.kind: BlockKind
    get() = when (this) {
        is TapBlock -> BlockKind.Tap
        is HoldBlock -> BlockKind.Hold
        is SwipeBlock -> BlockKind.Swipe
        is WaitBlock -> BlockKind.Wait
        is RandomWaitBlock -> BlockKind.RandomWait
        is RepeatBlock -> BlockKind.Repeat
    }

/** A new block of [kind], pointing at sensible targets from [board]. */
fun BlockKind.create(board: Board): Block {
    val first = board.targets.getOrNull(0)?.id
    val second = board.targets.getOrNull(1)?.id ?: first
    return when (this) {
        BlockKind.Tap -> TapBlock(id = newId(), target = first)
        BlockKind.Hold -> HoldBlock(id = newId(), target = first)
        BlockKind.Swipe -> SwipeBlock(id = newId(), from = first, to = second)
        BlockKind.Wait -> WaitBlock(id = newId())
        BlockKind.RandomWait -> RandomWaitBlock(id = newId())
        BlockKind.Repeat -> RepeatBlock(id = newId())
    }
}

/** Every target id a block (and its children) refers to. */
fun Block.referencedTargets(): List<String?> = when (this) {
    is TapBlock -> listOf(target)
    is HoldBlock -> listOf(target)
    is SwipeBlock -> listOf(from, to)
    is RepeatBlock -> children.flatMap { it.referencedTargets() }
    is WaitBlock, is RandomWaitBlock -> emptyList()
}
