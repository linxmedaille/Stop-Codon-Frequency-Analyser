package com.tapflow.autoclicker.ui.pages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.autoclicker.data.Store
import com.tapflow.autoclicker.service.RunStatus
import com.tapflow.autoclicker.ui.AppUi
import com.tapflow.autoclicker.ui.SheetRequest
import com.tapflow.autoclicker.ui.board.BoardPreviewCard
import com.tapflow.autoclicker.ui.components.PageGutter
import com.tapflow.autoclicker.ui.components.PageHeader
import com.tapflow.autoclicker.ui.components.RunBanner
import com.tapflow.autoclicker.ui.components.TopChips
import com.tapflow.autoclicker.ui.glass.GlassCard
import com.tapflow.autoclicker.ui.glass.GlassChip
import com.tapflow.autoclicker.ui.glass.Hairline
import com.tapflow.autoclicker.ui.glass.IconTile
import com.tapflow.autoclicker.ui.glass.Kicker
import com.tapflow.autoclicker.ui.glass.PrimaryButton
import com.tapflow.autoclicker.ui.glass.SettingRow
import com.tapflow.autoclicker.ui.glass.TargetDot
import com.tapflow.autoclicker.ui.glass.ValuePill
import com.tapflow.autoclicker.ui.glass.rememberHaptics
import com.tapflow.autoclicker.ui.theme.Tf
import com.tapflow.autoclicker.ui.theme.TfType
import com.tapflow.core.Mode
import com.tapflow.core.MultiConfig
import com.tapflow.core.formatDuration
import kotlin.math.roundToInt

private fun Store.updateMulti(transform: (MultiConfig) -> MultiConfig) = update { it.copy(multi = transform(it.multi)) }

private val DELAY_PRESETS = listOf(250L, 500L, 1_000L, 2_000L, 5_000L)

@Composable
fun MultiPage(
    store: Store,
    ui: AppUi,
    serviceOn: Boolean,
    status: RunStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTune: () -> Unit,
) {
    val ws by store.workspace.collectAsStateWithLifecycle()
    val cfg = ws.multi
    val targets = cfg.board.targets
    val c = Tf.colors
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val cycleMs = targets.sumOf { cfg.delayFor(it.id) + cfg.holdMs } + cfg.cycleDelayMs

    PageScroll {
        TopChips(serviceOn, onService = { ui.show(SheetRequest.Permission) }, onTune = onTune) {
            DELAY_PRESETS.forEach { ms ->
                val all = targets.isNotEmpty() && targets.all { cfg.delayFor(it.id) == ms }
                GlassChip(
                    "Every ${formatDuration(ms)}",
                    selected = all,
                    onClick = { store.updateMulti { it.copy(defaultDelayMs = ms, delays = emptyMap()) } },
                )
            }
        }
        RunBanner(status, onStop)
        PageHeader(
            title = "Multi tap",
            subtitle = "Several points, in order, on a loop",
            badge = "${targets.size} point${if (targets.size == 1) "" else "s"}",
        )
        BoardPreviewCard(
            board = cfg.board,
            onEdit = { ui.editor = Mode.Multi },
            onPickScreenshot = { ui.pickScreenshot(Mode.Multi) },
            modifier = Modifier.padding(horizontal = PageGutter),
        )
        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.padding(horizontal = PageGutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kicker("Sequence", Modifier.weight(1f))
                if (targets.size > 1) Text("hold ⋮⋮ to reorder", style = TfType.caption, color = c.inkTertiary)
            }
            Spacer(Modifier.height(8.dp))
            if (targets.isEmpty()) {
                Text(
                    "No points yet. Open the screenshot and tap where each click should land, in order.",
                    style = TfType.body,
                    color = c.inkSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            // Reorderable list: long-press a row and drag it.
            var draggingIndex by remember { mutableStateOf(-1) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            val rowHeight = 64.dp
            val rowPx = with(density) { rowHeight.toPx() }
            val hoverIndex = if (draggingIndex >= 0) {
                (draggingIndex + (dragOffset / rowPx).roundToInt()).coerceIn(0, targets.lastIndex)
            } else {
                -1
            }
            Column {
                targets.forEachIndexed { i, t ->
                    val isDragged = i == draggingIndex
                    val shift = when {
                        draggingIndex < 0 || isDragged -> 0f
                        i in (draggingIndex + 1)..hoverIndex -> -rowPx
                        i in hoverIndex until draggingIndex -> rowPx
                        else -> 0f
                    }
                    val animatedShift by animateFloatAsState(shift, spring(0.8f, 600f), label = "reorder")
                    Row(
                        Modifier
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragged) dragOffset else animatedShift
                                if (isDragged) {
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    shadowElevation = 12.dp.toPx()
                                    shape = RoundedCornerShape(20.dp)
                                    clip = true
                                }
                            }
                            .fillMaxWidth()
                            .height(rowHeight)
                            .background(if (isDragged) c.glassStrong else Color.Transparent, RoundedCornerShape(20.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp, rowHeight)
                                .pointerInput(targets.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.heavy()
                                            draggingIndex = i
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            val from = draggingIndex
                                            val to = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, targets.lastIndex)
                                            if (from >= 0 && to >= 0 && to != from) {
                                                store.updateBoard(Mode.Multi) { b ->
                                                    val list = b.targets.toMutableList()
                                                    val moved = list.removeAt(from)
                                                    list.add(to, moved)
                                                    b.copy(targets = list)
                                                }
                                                haptics.tap()
                                            }
                                            draggingIndex = -1
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = -1
                                            dragOffset = 0f
                                        },
                                    ) { change, drag ->
                                        change.consume()
                                        val before = (draggingIndex + (dragOffset / rowPx).roundToInt()).coerceIn(0, targets.lastIndex)
                                        dragOffset += drag.y
                                        val after = (draggingIndex + (dragOffset / rowPx).roundToInt()).coerceIn(0, targets.lastIndex)
                                        if (before != after) haptics.tick()
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.DragIndicator, "Reorder", tint = c.inkTertiary)
                        }
                        TargetDot(i, size = 32.dp, textStyle = TfType.label)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Tap ${t.name}", style = TfType.label.copy(fontSize = TfType.body.fontSize), color = c.ink)
                            Text("then wait", style = TfType.caption, color = c.inkSecondary)
                        }
                        ValuePill(formatDuration(cfg.delayFor(t.id)), onClick = {
                            ui.show(
                                SheetRequest.Duration(
                                    title = "Wait after ${t.name}",
                                    valueMs = cfg.delayFor(t.id),
                                    onSet = { v -> store.updateMulti { it.copy(delays = it.delays + (t.id to v)) } },
                                ),
                            )
                        })
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable { ui.editor = Mode.Multi }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).background(c.accentSoft, RoundedCornerShape(percent = 50)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Add points on the screenshot", style = TfType.label, color = c.accent)
            }
            Hairline(Modifier.padding(vertical = 10.dp))
            SettingRow(
                title = "Pause after each cycle",
                subtitle = "Extra wait once the last point is tapped",
                leading = { IconTile(Icons.Rounded.HourglassBottom, Color(0xFFF2A007)) },
            ) {
                ValuePill(if (cfg.cycleDelayMs == 0L) "None" else formatDuration(cfg.cycleDelayMs), onClick = {
                    ui.show(
                        SheetRequest.Duration(
                            title = "Pause after each cycle",
                            valueMs = cfg.cycleDelayMs,
                            presets = listOf(0, 500, 1_000, 2_000, 5_000, 10_000, 30_000, 60_000),
                            onSet = { v -> store.updateMulti { it.copy(cycleDelayMs = v) } },
                        ),
                    )
                })
            }
            SettingRow(
                title = "Hold each tap",
                leading = { IconTile(Icons.Rounded.Timer, Color(0xFF7B5CFF)) },
            ) {
                ValuePill(formatDuration(cfg.holdMs), onClick = {
                    ui.show(
                        SheetRequest.Duration(
                            title = "Hold each tap for",
                            valueMs = cfg.holdMs,
                            minMs = 1,
                            maxMs = 60_000,
                            presets = listOf(1, 10, 20, 50, 100, 250, 500, 1_000),
                            onSet = { v -> store.updateMulti { it.copy(holdMs = v) } },
                        ),
                    )
                })
            }
        }
        Spacer(Modifier.height(14.dp))
        RepeatCard(
            ui = ui,
            policy = cfg.repeat,
            onChange = { p -> store.updateMulti { it.copy(repeat = p) } },
            cycleMs = cycleMs.coerceAtLeast(1),
            noun = "cycles",
            modifier = Modifier.padding(horizontal = PageGutter),
        )
        Spacer(Modifier.height(14.dp))
        HumanizeCard(
            humanize = cfg.humanize,
            onChange = { h -> store.updateMulti { it.copy(humanize = h) } },
            modifier = Modifier.padding(horizontal = PageGutter),
        )
        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            text = "Start sequence",
            onClick = onStart,
            icon = Icons.Rounded.PlayArrow,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter),
        )
    }
}
