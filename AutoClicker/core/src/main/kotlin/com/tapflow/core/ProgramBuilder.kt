package com.tapflow.core

/** Turns the three editors' configs into a [Program]. */
object ProgramBuilder {

    /** Gestures need at least this long to register on most devices. */
    const val MIN_HOLD_MS = 1L

    fun single(config: SingleConfig, screen: ScreenSpace): BuildResult {
        val target = config.board.targets.firstOrNull()
            ?: return BuildResult(null, listOf(Issue(Severity.Error, "Place a tap point first.")))
        val hold = config.holdMs.coerceAtLeast(MIN_HOLD_MS)
        val wait = (config.intervalMs - hold).coerceAtLeast(0)
        val body = buildList {
            add(TapStep(screen.map(target.point), hold, target.id))
            if (wait > 0) add(WaitStep(wait, wait, null))
        }
        val issues = buildList {
            if (config.intervalMs < 20) {
                add(Issue(Severity.Warning, "Under 20 ms some apps can't keep up and will drop taps."))
            }
        }
        return BuildResult(
            program = Program(
                steps = listOf(LoopStep(config.repeat.loopTimes, body, RUNS_LOOP_ID)),
                screen = screen,
                timeLimitMs = config.repeat.timeLimitMs,
                humanize = config.humanize,
                markers = listOf(Marker(target.name, screen.map(target.point))),
                title = "Single · ${target.name}",
            ),
            issues = issues,
        )
    }

    fun multi(config: MultiConfig, screen: ScreenSpace): BuildResult {
        val targets = config.board.targets
        if (targets.isEmpty()) {
            return BuildResult(null, listOf(Issue(Severity.Error, "Add at least one tap point.")))
        }
        val hold = config.holdMs.coerceAtLeast(MIN_HOLD_MS)
        val body = buildList {
            for (t in targets) {
                add(TapStep(screen.map(t.point), hold, t.id))
                val d = config.delayFor(t.id)
                if (d > 0) add(WaitStep(d, d, null))
            }
            if (config.cycleDelayMs > 0) add(WaitStep(config.cycleDelayMs, config.cycleDelayMs, null))
        }
        val totalWait = targets.sumOf { config.delayFor(it.id) } + config.cycleDelayMs
        val issues = buildList {
            if (totalWait == 0L && config.repeat.mode != RepeatMode.Times) {
                add(Issue(Severity.Warning, "No delays: taps will fire as fast as the system allows."))
            }
        }
        return BuildResult(
            program = Program(
                steps = listOf(LoopStep(config.repeat.loopTimes, body, RUNS_LOOP_ID)),
                screen = screen,
                timeLimitMs = config.repeat.timeLimitMs,
                humanize = config.humanize,
                markers = targets.map { Marker(it.name, screen.map(it.point)) },
                title = "Multi · ${targets.size} points",
            ),
            issues = issues,
        )
    }

    fun chain(config: ChainConfig, screen: ScreenSpace): BuildResult {
        val issues = validate(config.blocks, config.board).toMutableList()
        if (config.blocks.isEmpty()) {
            issues += Issue(Severity.Error, "Add some blocks to the chain first.")
        }
        if (issues.any { it.severity == Severity.Error }) return BuildResult(null, issues)

        val board = config.board
        fun at(id: String?): PxPoint = screen.map(board.target(id)!!.point)

        fun compile(blocks: List<Block>): List<Step> = buildList {
            for (b in blocks) when (b) {
                is TapBlock -> {
                    val n = b.count.coerceIn(1, 50)
                    val hold = b.holdMs.coerceAtLeast(MIN_HOLD_MS)
                    repeat(n) { i ->
                        add(TapStep(at(b.target), hold, b.id))
                        if (i < n - 1 && b.gapMs > 0) add(WaitStep(b.gapMs, b.gapMs, b.id))
                    }
                }
                is HoldBlock -> add(TapStep(at(b.target), b.durationMs.coerceAtLeast(MIN_HOLD_MS), b.id))
                is SwipeBlock -> add(SwipeStep(at(b.from), at(b.to), b.durationMs.coerceAtLeast(10), b.id))
                is WaitBlock -> if (b.ms > 0) add(WaitStep(b.ms, b.ms, b.id))
                is RandomWaitBlock -> {
                    val lo = minOf(b.minMs, b.maxMs).coerceAtLeast(0)
                    val hi = maxOf(b.minMs, b.maxMs).coerceAtLeast(0)
                    if (hi > 0) add(WaitStep(lo, hi, b.id))
                }
                is RepeatBlock -> if (b.children.isNotEmpty()) add(LoopStep(b.times.coerceAtLeast(0), compile(b.children), b.id))
            }
        }

        val body = compile(config.blocks)
        val runs = config.runs
        val steps = if (runs.mode == RepeatMode.Times && runs.times <= 1) {
            body
        } else {
            listOf(LoopStep(runs.loopTimes, body, RUNS_LOOP_ID))
        }
        val used = config.blocks.flatMap { it.referencedTargets() }.filterNotNull().toSet()
        return BuildResult(
            program = Program(
                steps = steps,
                screen = screen,
                timeLimitMs = runs.timeLimitMs,
                humanize = config.humanize,
                markers = board.targets.filter { it.id in used }.map { Marker(it.name, screen.map(it.point)) },
                title = "Chain · ${BlockTree.count(config.blocks)} blocks",
            ),
            issues = issues,
        )
    }

    /** Problems the chain editor shows inline, next to the offending block. */
    fun validate(blocks: List<Block>, board: Board): List<Issue> = buildList {
        fun missing(id: String?) = board.target(id) == null

        fun walk(list: List<Block>) {
            list.forEachIndexed { i, b ->
                when (b) {
                    is TapBlock -> if (missing(b.target)) add(Issue(Severity.Error, "Pick a point to tap.", b.id))
                    is HoldBlock -> if (missing(b.target)) add(Issue(Severity.Error, "Pick a point to hold.", b.id))
                    is SwipeBlock -> if (missing(b.from) || missing(b.to)) {
                        add(Issue(Severity.Error, "Pick where the swipe starts and ends.", b.id))
                    }
                    is RandomWaitBlock -> if (b.minMs > b.maxMs) {
                        add(Issue(Severity.Warning, "Min is larger than max; they'll be swapped.", b.id))
                    }
                    is WaitBlock -> Unit
                    is RepeatBlock -> {
                        if (b.children.isEmpty()) {
                            add(Issue(Severity.Warning, "This repeat is empty.", b.id))
                        } else if (b.forever && !containsWait(b.children)) {
                            add(Issue(Severity.Warning, "Forever loop with no wait will tap as fast as possible.", b.id))
                        }
                        if (b.forever && i < list.lastIndex) {
                            add(Issue(Severity.Warning, "Blocks after a forever loop never run.", list[i + 1].id))
                        }
                        walk(b.children)
                    }
                }
            }
        }
        walk(blocks)
    }

    private fun containsWait(blocks: List<Block>): Boolean = blocks.any {
        when (it) {
            is WaitBlock -> it.ms > 0
            is RandomWaitBlock -> it.maxMs > 0
            is HoldBlock, is SwipeBlock -> true
            is TapBlock -> it.count > 1 && it.gapMs > 0
            is RepeatBlock -> containsWait(it.children)
        }
    }
}
