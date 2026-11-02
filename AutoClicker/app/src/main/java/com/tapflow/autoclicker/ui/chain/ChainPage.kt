package com.tapflow.autoclicker.ui.chain

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.autoclicker.data.Store
import com.tapflow.autoclicker.service.Phase
import com.tapflow.autoclicker.service.RunStatus
import com.tapflow.autoclicker.ui.AppUi
import com.tapflow.autoclicker.ui.SheetRequest
import com.tapflow.autoclicker.ui.board.rememberScreenshot
import com.tapflow.autoclicker.ui.components.DockClearance
import com.tapflow.autoclicker.ui.components.PageGutter
import com.tapflow.autoclicker.ui.components.PageHeader
import com.tapflow.autoclicker.ui.components.RunBanner
import com.tapflow.autoclicker.ui.components.TopChips
import com.tapflow.autoclicker.ui.glass.GlassCard
import com.tapflow.autoclicker.ui.glass.GlassChip
import com.tapflow.autoclicker.ui.glass.GlassDefaults
import com.tapflow.autoclicker.ui.glass.GlassSurface
import com.tapflow.autoclicker.ui.glass.Kicker
import com.tapflow.autoclicker.ui.glass.PrimaryButton
import com.tapflow.autoclicker.ui.glass.TargetDot
import com.tapflow.autoclicker.ui.glass.rememberHaptics
import com.tapflow.autoclicker.ui.pages.HumanizeCard
import com.tapflow.autoclicker.ui.theme.Tf
import com.tapflow.autoclicker.ui.theme.TfType
import com.tapflow.core.Block
import com.tapflow.core.BlockKind
import com.tapflow.core.BlockTree
import com.tapflow.core.ChainConfig
import com.tapflow.core.Estimate
import com.tapflow.core.Issue
import com.tapflow.core.Mode
import com.tapflow.core.ProgramBuilder
import com.tapflow.core.RepeatBlock
import com.tapflow.core.RepeatMode
import com.tapflow.core.RepeatPolicy
import com.tapflow.core.RowKind
import com.tapflow.core.Severity
import com.tapflow.core.create
import com.tapflow.core.formatCount
import com.tapflow.core.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Chain editor state shared between the page and the block palette above the dock. */
@Stable
class ChainUiState {
    var selectedId by mutableStateOf<String?>(null)
    var scrollTo by mutableStateOf<String?>(null)
}

private fun Store.updateChain(transform: (ChainConfig) -> ChainConfig) = update { it.copy(chain = transform(it.chain)) }

/** Adds a new block next to the selection (or inside a selected repeat). */
fun addBlock(store: Store, state: ChainUiState, kind: BlockKind) {
    var newId: String? = null
    store.updateChain { chain ->
        val block = kind.create(chain.board)
        newId = block.id
        val sel = state.selectedId?.let { BlockTree.find(chain.blocks, it) }
        val blocks = when {
            sel == null -> chain.blocks + block
            sel is RepeatBlock -> BlockTree.append(chain.blocks, sel.id, block)
            else -> {
                val (parent, index) = BlockTree.locate(chain.blocks, sel.id) ?: (null to chain.blocks.size)
                BlockTree.insert(chain.blocks, parent, index + 1, block)
            }
        }
        chain.copy(blocks = blocks)
    }
    // Keep a selected repeat selected so several blocks can be dropped into it in a row.
    if (state.selectedId?.let { BlockTree.find(store.workspace.value.chain.blocks, it) } !is RepeatBlock) {
        state.selectedId = newId
    }
    state.scrollTo = newId
}

@Composable
fun ChainPage(
    store: Store,
    ui: AppUi,
    state: ChainUiState,
    serviceOn: Boolean,
    status: RunStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTune: () -> Unit,
) {
    val ws by store.workspace.collectAsStateWithLifecycle()
    val chain = ws.chain
    val board = chain.board
    val blocks = chain.blocks
    val rows = remember(blocks) { BlockTree.flatten(blocks) }
    val issues: Map<String, Issue> = remember(blocks, board) {
        ProgramBuilder.validate(blocks, board)
            .filter { it.blockId != null }
            .groupBy { it.blockId!! }
            .mapValues { (_, list) -> list.minByOrNull { if (it.severity == Severity.Error) 0 else 1 }!! }
    }
    val listState = rememberLazyListState()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // ---- drag & drop state ----
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragKeys by remember { mutableStateOf(emptySet<String>()) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    var gapIndex by remember { mutableStateOf<Int?>(null) }
    var gapY by remember { mutableFloatStateOf(0f) }
    var gapDepth by remember { mutableStateOf(0) }
    val liveBlocks by rememberUpdatedState(blocks)
    val liveRows by rememberUpdatedState(rows)

    val actions = remember(ui, store, state) {
        object : BlockActions {
            override fun select(id: String) {
                state.selectedId = if (state.selectedId == id) null else id
            }

            override fun pickTarget(blockId: String, current: String?, title: String, onPick: (String) -> Unit) {
                ui.show(
                    SheetRequest.PickTarget(
                        title = title,
                        board = store.workspace.value.chain.board,
                        selected = current,
                        onPick = onPick,
                        onEditPoints = { ui.editor = Mode.Chain },
                    ),
                )
            }

            override fun editDuration(title: String, valueMs: Long, onSet: (Long) -> Unit) {
                ui.show(SheetRequest.Duration(title = title, valueMs = valueMs, onSet = onSet))
            }

            override fun editCount(title: String, value: Int, allowForever: Boolean, onSet: (Int) -> Unit) {
                ui.show(SheetRequest.Count(title = title, value = value, allowForever = allowForever, presets = listOf(2, 3, 5, 10, 20, 50, 100), onSet = onSet))
            }

            override fun update(id: String, transform: (Block) -> Block) {
                store.updateChain { it.copy(blocks = BlockTree.update(it.blocks, id, transform)) }
            }

            override fun showIssue(issue: Issue) {
                ui.toast(issue.message, isError = issue.severity == Severity.Error)
            }
        }
    }

    // Scroll newly added blocks into view.
    LaunchedEffect(state.scrollTo, rows) {
        val target = state.scrollTo ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.key == target }
        if (index >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo.any { it.key == target }
            if (!visible) listState.animateScrollToItem(index + HEADER_ITEMS, scrollOffset = -200)
            state.scrollTo = null
        }
    }

    fun itemTop(info: androidx.compose.foundation.lazy.LazyListItemInfo): Float =
        (info.offset - listState.layoutInfo.viewportStartOffset).toFloat()

    fun recomputeGap(pointerY: Float) {
        val id = dragId ?: return
        val remaining = BlockTree.flatten(BlockTree.remove(liveBlocks, id).first)
        val index = remaining.withIndex().associate { it.value.key to it.index }
        val visible = listState.layoutInfo.visibleItemsInfo.filter { (it.key as? String) in index }
        val below = visible.firstOrNull { itemTop(it) + it.size / 2f > pointerY }
        val g: Int
        val y: Float
        when {
            below != null -> {
                g = index.getValue(below.key as String)
                y = itemTop(below)
            }
            visible.isNotEmpty() -> {
                val last = visible.last()
                g = index.getValue(last.key as String) + 1
                y = itemTop(last) + last.size
            }
            else -> {
                g = 0
                y = pointerY
            }
        }
        if (g != gapIndex) haptics.tick()
        gapIndex = g
        gapY = y
        gapDepth = BlockTree.slotAt(remaining, g).depth
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val hitInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                        val top = itemTop(it)
                        down.position.y >= top && down.position.y < top + it.size
                    } ?: return@awaitEachGesture
                    val key = hitInfo.key as? String ?: return@awaitEachGesture
                    val row = liveRows.firstOrNull { it.key == key } ?: return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    // Wait for a long press without moving.
                    val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                            if (!ch.pressed || ch.isConsumed) return@withTimeoutOrNull true
                            if ((ch.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
                        }
                        @Suppress("UNREACHABLE_CODE")
                        true
                    }
                    if (cancelled == true) return@awaitEachGesture

                    // Long press: pick the block (and everything inside it) up.
                    val id = row.block.id
                    val start = liveRows.indexOfFirst { it.key == id }
                    val end = if (row.block is RepeatBlock) liveRows.indexOfFirst { it.key == "$id#end" } else start
                    dragKeys = liveRows.subList(start, end + 1).map { it.key }.toSet()
                    dragId = id
                    state.selectedId = null
                    haptics.heavy()
                    var pointerY = down.position.y
                    var scrolled = 0f
                    dragDy = 0f
                    recomputeGap(pointerY)

                    val edgeJob: Job = scope.launch {
                        val edge = with(density) { 90.dp.toPx() }
                        val maxSpeed = with(density) { 14.dp.toPx() }
                        while (isActive) {
                            withFrameNanos { }
                            val h = size.height.toFloat()
                            val speed = when {
                                pointerY < edge -> -maxSpeed * (1f - pointerY / edge).coerceIn(0f, 1f)
                                pointerY > h - edge -> maxSpeed * (1f - (h - pointerY) / edge).coerceIn(0f, 1f)
                                else -> 0f
                            }
                            if (speed != 0f) {
                                val used = listState.scrollBy(speed)
                                scrolled += used
                                dragDy = pointerY - down.position.y + scrolled
                                recomputeGap(pointerY)
                            }
                        }
                    }
                    try {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                            e.changes.forEach { it.consume() }
                            if (!ch.pressed) break
                            pointerY = ch.position.y
                            dragDy = pointerY - down.position.y + scrolled
                            recomputeGap(pointerY)
                        }
                    } finally {
                        edgeJob.cancel()
                        val g = gapIndex
                        if (g != null) {
                            store.updateChain { it.copy(blocks = BlockTree.moveToGap(it.blocks, id, g)) }
                            haptics.tap()
                        }
                        dragId = null
                        dragKeys = emptySet()
                        dragDy = 0f
                        gapIndex = null
                    }
                }
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topInset + 6.dp, bottom = DockClearance + 96.dp),
        ) {
            item(key = "hdr:chips") {
                TopChips(serviceOn, onService = { ui.show(SheetRequest.Permission) }, onTune = onTune) {
                    val oneRun = Estimate.durationMs(blocks)
                    GlassChip(
                        text = when (chain.runs.mode) {
                            RepeatMode.Forever -> "Runs forever"
                            RepeatMode.Times -> if (chain.runs.times <= 1) "Runs once" else "Runs ${chain.runs.times}×"
                            RepeatMode.Duration -> "Runs ${formatDuration(chain.runs.durationMs)}"
                        },
                        onClick = { editRuns(ui, store, chain.runs) },
                    )
                    GlassChip(
                        text = if (oneRun == null) "≈ ∞ per run" else "≈ ${formatDuration(oneRun)} per run",
                        onClick = { },
                        leading = { Icon(Icons.Rounded.Schedule, null, tint = Tf.colors.ink, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            item(key = "hdr:banner") { RunBanner(status, onStop) }
            item(key = "hdr:title") {
                PageHeader(
                    title = "Chain",
                    subtitle = "Snap blocks together · hold to drag",
                    badge = "${BlockTree.count(blocks)} blocks",
                )
            }
            item(key = "hdr:points") {
                PointsStrip(
                    board = board,
                    onEdit = { ui.editor = Mode.Chain },
                    onPick = { ui.pickScreenshot(Mode.Chain) },
                )
            }
            item(key = "hdr:hat") {
                HatBlock(
                    runs = chain.runs,
                    onRuns = { editRuns(ui, store, chain.runs) },
                    modifier = Modifier.padding(start = PageGutter, end = PageGutter, top = 18.dp),
                )
            }
            items(rows, key = { it.key }, contentType = { it.kind }) { row ->
                val lifted = row.key in dragKeys
                val running = status.phase == Phase.Running && status.currentBlock == row.block.id && row.kind != RowKind.RepeatEnd
                BlockRow(
                    row = row,
                    board = board,
                    issue = if (row.kind == RowKind.RepeatEnd) null else issues[row.block.id],
                    selected = state.selectedId == row.block.id && row.kind != RowKind.RepeatEnd,
                    running = running,
                    lifted = lifted,
                    actions = actions,
                    modifier = Modifier
                        .then(if (lifted) Modifier.zIndex(10f) else Modifier.animateItem(fadeInSpec = spring(stiffness = 600f), placementSpec = spring(dampingRatio = 0.8f, stiffness = 500f), fadeOutSpec = spring(stiffness = 600f)))
                        .padding(horizontal = PageGutter)
                        .graphicsLayer { translationY = if (lifted) dragDy else 0f },
                )
            }
            if (rows.isEmpty()) {
                item(key = "hdr:empty") {
                    Text(
                        "Tap a block in the tray below to add it.",
                        style = TfType.body,
                        color = Tf.colors.inkSecondary,
                        modifier = Modifier.padding(horizontal = PageGutter + 8.dp, vertical = 18.dp),
                    )
                }
            }
            item(key = "ftr:summary") {
                ChainSummary(chain, issues.values.toList(), Modifier.padding(start = PageGutter, end = PageGutter, top = 28.dp))
            }
            item(key = "ftr:humanize") {
                HumanizeCard(
                    humanize = chain.humanize,
                    onChange = { h -> store.updateChain { it.copy(humanize = h) } },
                    modifier = Modifier.padding(start = PageGutter, end = PageGutter, top = 14.dp),
                )
            }
            item(key = "ftr:start") {
                PrimaryButton(
                    text = "Start chain",
                    onClick = onStart,
                    icon = Icons.Rounded.PlayArrow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = PageGutter, end = PageGutter, top = 22.dp),
                )
            }
        }

        // Insertion marker while dragging.
        if (dragId != null && gapIndex != null) {
            val c = Tf.colors
            Canvas(Modifier.fillMaxSize()) {
                val x = PageGutter.toPx() + BlockMetrics.arm.toPx() * gapDepth
                val y = gapY
                val w = size.width - x - PageGutter.toPx()
                drawRoundRect(c.accent.copy(alpha = 0.25f), Offset(x - 4.dp.toPx(), y - 7.dp.toPx()), Size(w + 8.dp.toPx(), 14.dp.toPx()), CornerRadius(7.dp.toPx()))
                drawRoundRect(c.accent, Offset(x, y - 3.dp.toPx()), Size(w, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                drawCircle(Color.White, 6.dp.toPx(), Offset(x, y))
                drawCircle(c.accent, 4.dp.toPx(), Offset(x, y))
            }
        }
    }
}

private const val HEADER_ITEMS = 5

private fun editRuns(ui: AppUi, store: Store, runs: RepeatPolicy) {
    ui.show(
        SheetRequest.Count(
            title = "Run the chain how many times?",
            value = if (runs.mode == RepeatMode.Forever) 0 else runs.times,
            allowForever = true,
            presets = listOf(1, 2, 3, 5, 10, 25, 100),
            onSet = { n ->
                store.update {
                    it.copy(
                        chain = it.chain.copy(
                            runs = if (n <= 0) RepeatPolicy(RepeatMode.Forever) else RepeatPolicy(RepeatMode.Times, times = n),
                        ),
                    )
                }
            },
        ),
    )
}

/** The chain's points: a small screenshot thumbnail and a chip per point. */
@Composable
private fun PointsStrip(board: com.tapflow.core.Board, onEdit: () -> Unit, onPick: () -> Unit) {
    val c = Tf.colors
    val image = rememberScreenshot(board.screenshot)
    GlassCard(
        modifier = Modifier.padding(horizontal = PageGutter),
        padding = PaddingValues(12.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp, 76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (c.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                    .clickable { if (board.screenshot == null) onPick() else onEdit() },
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    androidx.compose.foundation.Image(
                        image,
                        null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Rounded.TouchApp, null, tint = c.inkTertiary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Kicker("Points")
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (board.targets.isEmpty()) {
                        Text("None yet", style = TfType.label, color = c.inkSecondary)
                    }
                    board.targets.forEachIndexed { i, t ->
                        Row(
                            Modifier
                                .background(c.hairline.copy(alpha = c.hairline.alpha * 0.7f), RoundedCornerShape(percent = 50))
                                .padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TargetDot(i, size = 22.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(t.name, style = TfType.label, color = c.ink)
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            com.tapflow.autoclicker.ui.glass.GlassIconButton(Icons.Rounded.CenterFocusStrong, "Edit points", onEdit, size = 48.dp)
        }
    }
}

@Composable
private fun ChainSummary(chain: ChainConfig, issues: List<Issue>, modifier: Modifier = Modifier) {
    val c = Tf.colors
    val oneRun = Estimate.durationMs(chain.blocks)
    val gestures = Estimate.gestures(chain.blocks)
    GlassCard(modifier) {
        Kicker("One run")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (oneRun == null) "∞" else formatDuration(oneRun), style = TfType.number, color = c.ink)
            Spacer(Modifier.width(12.dp))
            Text(
                if (gestures == null) "loops until stopped" else "${formatCount(gestures)} gestures",
                style = TfType.body,
                color = c.inkSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        val errors = issues.count { it.severity == Severity.Error }
        val warnings = issues.size - errors
        if (issues.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    if (errors > 0) append("$errors block${if (errors > 1) "s" else ""} need${if (errors == 1) "s" else ""} a point")
                    if (errors > 0 && warnings > 0) append(" · ")
                    if (warnings > 0) append("$warnings tip${if (warnings > 1) "s" else ""}")
                    append(" (tap the ! on a block)")
                },
                style = TfType.label,
                color = if (errors > 0) c.danger else c.warning,
            )
        }
    }
}

/**
 * The block palette that floats above the dock on the chain page. When a block is
 * selected it turns into an action bar for it.
 */
@Composable
fun ChainTray(
    store: Store,
    state: ChainUiState,
    modifier: Modifier = Modifier,
    backdrop: com.tapflow.autoclicker.ui.glass.Backdrop?,
) {
    val c = Tf.colors
    val haptics = rememberHaptics()
    val ws by store.workspace.collectAsStateWithLifecycle()
    val selected = state.selectedId?.let { BlockTree.find(ws.chain.blocks, it) }
    LaunchedEffect(selected) {
        if (state.selectedId != null && selected == null) state.selectedId = null
    }
    GlassSurface(
        modifier = modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(percent = 50),
        style = GlassDefaults.regular(c).copy(blur = 20.dp, refraction = 0.7f, bezel = 20.dp),
        backdrop = backdrop,
        elevation = 16.dp,
    ) {
        AnimatedContent(
            targetState = selected?.let { it.id to (it is RepeatBlock) },
            transitionSpec = { fadeIn(spring(stiffness = 700f)) togetherWith fadeOut(spring(stiffness = 700f)) },
            label = "tray",
        ) { sel ->
            if (sel == null || sel.second) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sel != null) {
                        // A repeat is selected: new blocks go inside it.
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(c.chip)
                                .clickable { state.selectedId = null }
                                .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Into repeat", style = TfType.caption.copy(fontSize = TfType.label.fontSize * 0.9f), color = c.onChip)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.Close, "Deselect", tint = c.onChip, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Text("Add", style = TfType.kicker, color = c.inkTertiary, modifier = Modifier.padding(start = 6.dp, end = 2.dp))
                    }
                    BlockKind.entries.forEach { kind ->
                        PaletteBlock(kind, onClick = { addBlock(store, state, kind) })
                    }
                    Spacer(Modifier.width(4.dp))
                }
            } else {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrayAction(Icons.Rounded.ContentCopy, "Duplicate") {
                        haptics.confirm()
                        var newId: String? = null
                        store.update {
                            val (b, id) = BlockTree.duplicate(it.chain.blocks, sel.first)
                            newId = id
                            it.copy(chain = it.chain.copy(blocks = b))
                        }
                        state.selectedId = newId
                        state.scrollTo = newId
                    }
                    TrayAction(Icons.Rounded.DeleteOutline, "Delete", tint = c.danger) {
                        haptics.heavy()
                        store.update { it.copy(chain = it.chain.copy(blocks = BlockTree.remove(it.chain.blocks, sel.first).first)) }
                        state.selectedId = null
                    }
                    Spacer(Modifier.weight(1f))
                    TrayAction(Icons.Rounded.Close, "Done") { state.selectedId = null }
                }
            }
        }
    }
}

@Composable
private fun TrayAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Tf.colors.ink,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = TfType.label, color = tint)
    }
}

