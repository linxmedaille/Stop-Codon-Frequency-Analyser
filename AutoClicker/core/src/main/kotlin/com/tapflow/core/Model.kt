package com.tapflow.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A point on the screen in normalised coordinates: (0,0) is the top-left corner of the
 * physical display and (1,1) the bottom-right. Normalising keeps targets valid when a
 * screenshot is shown scaled inside the editor, or when the display resolution changes.
 */
@Serializable
data class NPoint(val x: Float, val y: Float) {
    fun clamped(): NPoint = NPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/** A named place to tap, e.g. "P1". */
@Serializable
data class Target(
    val id: String,
    val name: String,
    val point: NPoint,
)

/** A screenshot imported to set targets up visually. [path] is a private app file. */
@Serializable
data class Screenshot(
    val path: String,
    val width: Int,
    val height: Int,
) {
    val aspect: Float get() = if (height == 0) 0f else width.toFloat() / height
}

/** A screenshot plus the targets placed on it. Each mode keeps its own board. */
@Serializable
data class Board(
    val screenshot: Screenshot? = null,
    val targets: List<Target> = emptyList(),
) {
    fun target(id: String?): Target? = if (id == null) null else targets.firstOrNull { it.id == id }

    fun indexOf(id: String?): Int = targets.indexOfFirst { it.id == id }

    /** The next unused name: "P1", "P2", ... */
    fun nextName(): String {
        val used = targets.map { it.name }.toSet()
        var n = 1
        while ("P$n" in used) n++
        return "P$n"
    }

    fun addTarget(point: NPoint): Pair<Board, Target> {
        val t = Target(id = newId(), name = nextName(), point = point.clamped())
        return copy(targets = targets + t) to t
    }

    fun moveTarget(id: String, point: NPoint): Board =
        copy(targets = targets.map { if (it.id == id) it.copy(point = point.clamped()) else it })

    fun removeTarget(id: String): Board = copy(targets = targets.filterNot { it.id == id })

    fun renameTarget(id: String, name: String): Board =
        copy(targets = targets.map { if (it.id == id) it.copy(name = name) else it })
}

@Serializable
enum class RepeatMode { Forever, Times, Duration }

/** How long a whole routine keeps going. */
@Serializable
data class RepeatPolicy(
    val mode: RepeatMode = RepeatMode.Forever,
    val times: Int = 100,
    val durationMs: Long = 60_000,
) {
    /** Loop count for [ProgramBuilder]: 0 means "until stopped / until the time limit". */
    val loopTimes: Int get() = if (mode == RepeatMode.Times) times.coerceAtLeast(1) else 0
    val timeLimitMs: Long? get() = if (mode == RepeatMode.Duration) durationMs.coerceAtLeast(1) else null
}

/** Small random variations so taps look less robotic. */
@Serializable
data class Humanize(
    val enabled: Boolean = false,
    /** Taps land anywhere inside a circle of this radius (pixels) around the target. */
    val offsetPx: Int = 8,
    /** Waits vary by up to this percentage either way. */
    val timingPct: Int = 10,
)

@Serializable
data class SingleConfig(
    val board: Board = Board(),
    /** Time from the start of one tap to the start of the next. */
    val intervalMs: Long = 100,
    /** How long each tap is held down. */
    val holdMs: Long = 20,
    val repeat: RepeatPolicy = RepeatPolicy(),
    val humanize: Humanize = Humanize(),
)

@Serializable
data class MultiConfig(
    val board: Board = Board(),
    /** Wait after tapping each target, keyed by target id. Missing entries use [defaultDelayMs]. */
    val delays: Map<String, Long> = emptyMap(),
    val defaultDelayMs: Long = 500,
    val holdMs: Long = 20,
    /** Extra pause after the last target of every cycle. */
    val cycleDelayMs: Long = 0,
    val repeat: RepeatPolicy = RepeatPolicy(mode = RepeatMode.Times, times = 10),
    val humanize: Humanize = Humanize(),
) {
    fun delayFor(targetId: String): Long = delays[targetId] ?: defaultDelayMs
}

@Serializable
data class ChainConfig(
    val board: Board = defaultChainBoard(),
    val blocks: List<Block> = defaultChainBlocks(board),
    /** How many times the whole chain runs. */
    val runs: RepeatPolicy = RepeatPolicy(mode = RepeatMode.Times, times = 1),
    val humanize: Humanize = Humanize(),
)

@Serializable
enum class Mode { Single, Multi, Chain }

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
data class Preferences(
    val theme: ThemeMode = ThemeMode.System,
    val haptics: Boolean = true,
    /** Show numbered markers on screen where taps will land. */
    val showMarkers: Boolean = true,
    /** Pressing volume-down while running stops the routine. */
    val volumeKeyStop: Boolean = true,
    /** Seconds to count down after pressing play on the floating panel. */
    val countdownSec: Int = 3,
    val reduceMotion: Boolean = false,
)

/** Everything the app persists. */
@Serializable
data class Workspace(
    val version: Int = 1,
    val single: SingleConfig = SingleConfig(),
    val multi: MultiConfig = MultiConfig(),
    val chain: ChainConfig = ChainConfig(),
    val prefs: Preferences = Preferences(),
    val lastMode: Mode = Mode.Single,
    val onboarded: Boolean = false,
)

private const val ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

fun newId(random: Random = Random.Default): String =
    buildString(10) { repeat(10) { append(ID_ALPHABET[random.nextInt(ID_ALPHABET.length)]) } }

internal fun defaultChainBoard(): Board = Board(
    targets = listOf(
        Target(id = "p1", name = "P1", point = NPoint(0.3f, 0.42f)),
        Target(id = "p2", name = "P2", point = NPoint(0.7f, 0.62f)),
    ),
)

/** The example from the brief: tap P1, wait 20 s, tap P2, then a small repeat loop. */
internal fun defaultChainBlocks(board: Board): List<Block> {
    val p1 = board.targets.getOrNull(0)?.id
    val p2 = board.targets.getOrNull(1)?.id
    return listOf(
        TapBlock(id = "b1", target = p1),
        WaitBlock(id = "b2", ms = 20_000),
        TapBlock(id = "b3", target = p2),
        RepeatBlock(
            id = "b4",
            times = 5,
            children = listOf(
                TapBlock(id = "b5", target = p1),
                WaitBlock(id = "b6", ms = 1_000),
            ),
        ),
    )
}
