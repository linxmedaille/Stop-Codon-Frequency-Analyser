package com.tapflow.core

/** A point in physical screen pixels. */
data class PxPoint(val x: Float, val y: Float)

/** The physical display the program runs on. */
data class ScreenSpace(val width: Int, val height: Int) {
    fun map(p: NPoint): PxPoint = PxPoint(
        x = (p.x * width).coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat()),
        y = (p.y * height).coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat()),
    )
}

/** Steps of a compiled program, in pixels. [sourceId] links a step back to its block. */
sealed interface Step {
    val sourceId: String?
}

data class TapStep(val at: PxPoint, val holdMs: Long, override val sourceId: String?) : Step

data class SwipeStep(
    val from: PxPoint,
    val to: PxPoint,
    val durationMs: Long,
    override val sourceId: String?,
) : Step

/** Waits [minMs]..[maxMs] (equal for a fixed wait). */
data class WaitStep(val minMs: Long, val maxMs: Long, override val sourceId: String?) : Step

/** Runs [body] [times] times, or forever when [times] is 0. */
data class LoopStep(val times: Int, val body: List<Step>, override val sourceId: String?) : Step {
    val forever: Boolean get() = times <= 0
}

/** A labelled marker shown on screen while running. */
data class Marker(val name: String, val at: PxPoint)

data class Program(
    val steps: List<Step>,
    val screen: ScreenSpace,
    val timeLimitMs: Long? = null,
    val humanize: Humanize = Humanize(),
    val markers: List<Marker> = emptyList(),
    /** A short description for the floating panel, e.g. "Single · P1". */
    val title: String = "",
)

enum class Severity { Error, Warning }

data class Issue(val severity: Severity, val message: String, val blockId: String? = null)

/** A program ready to run, or the errors that stop it from running. */
data class BuildResult(val program: Program?, val issues: List<Issue>) {
    val errors: List<Issue> get() = issues.filter { it.severity == Severity.Error }
    val warnings: List<Issue> get() = issues.filter { it.severity == Severity.Warning }
}

/** The id the outer "run N times" loop reports progress under. */
const val RUNS_LOOP_ID = "__runs"
