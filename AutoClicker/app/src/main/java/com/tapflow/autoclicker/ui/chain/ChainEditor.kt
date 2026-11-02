package com.tapflow.autoclicker.ui.chain

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tapflow.autoclicker.ui.glass.animatedPressScale
import com.tapflow.autoclicker.ui.glass.rememberHaptics
import com.tapflow.autoclicker.ui.glass.targetColor
import com.tapflow.autoclicker.ui.theme.TfType
import com.tapflow.core.Block
import com.tapflow.core.Board
import com.tapflow.core.FlatRow
import com.tapflow.core.HoldBlock
import com.tapflow.core.Issue
import com.tapflow.core.RandomWaitBlock
import com.tapflow.core.RepeatBlock
import com.tapflow.core.RepeatMode as CoreRepeatMode
import com.tapflow.core.RepeatPolicy
import com.tapflow.core.RowKind
import com.tapflow.core.Severity
import com.tapflow.core.SwipeBlock
import com.tapflow.core.TapBlock
import com.tapflow.core.WaitBlock
import com.tapflow.core.formatDurationShort
import com.tapflow.core.kind

/** Callbacks a block row needs; implemented by the chain page. */
interface BlockActions {
    fun select(id: String)
    fun pickTarget(blockId: String, current: String?, title: String, onPick: (String) -> Unit)
    fun editDuration(title: String, valueMs: Long, onSet: (Long) -> Unit)
    fun editCount(title: String, value: Int, allowForever: Boolean, onSet: (Int) -> Unit)
    fun update(id: String, transform: (Block) -> Block)
    fun showIssue(issue: Issue)
}

private fun Color.darken(f: Float) = lerp(this, Color.Black, f)
private fun Color.lighten(f: Float) = lerp(this, Color.White, f)

/**
 * One row of the flattened chain: a simple block, the top of a repeat, or a repeat's
 * end cap. Arms of enclosing repeats are drawn down the left side so nested blocks look
 * like they sit inside the C.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockRow(
    row: FlatRow,
    board: Board,
    issue: Issue?,
    selected: Boolean,
    running: Boolean,
    lifted: Boolean,
    actions: BlockActions,
    modifier: Modifier = Modifier,
) {
    val m = BlockMetrics
    val block = row.block
    val look = lookOf(block.kind)
    val repeatColor = lookOf(com.tapflow.core.BlockKind.Repeat).color
    val haptics = rememberHaptics()

    val shape = remember(row.kind) {
        when (row.kind) {
            RowKind.Block -> PuzzleShape(topNotchAt = m.notchX, bottomTabAt = m.notchX)
            RowKind.RepeatHeader -> PuzzleShape(topNotchAt = m.notchX, bottomTabAt = m.arm + m.notchX, squareBottomLeft = true)
            RowKind.RepeatEnd -> PuzzleShape(topNotchAt = m.arm + m.notchX, bottomTabAt = m.notchX, squareTopLeft = true)
        }
    }

    val glow = if (running) {
        val t = rememberInfiniteTransition(label = "run-glow")
        val a by t.animateFloat(0.25f, 0.9f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "run-a")
        a
    } else {
        0f
    }
    val selectAlpha by animateFloatAsState(if (selected) 1f else 0f, spring(0.8f, 600f), label = "sel")

    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                // Arms of the repeats this row sits inside.
                val armPx = m.arm.toPx()
                for (i in 0 until row.depth) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(repeatColor.lighten(0.08f), repeatColor, repeatColor.darken(0.06f)),
                            startX = i * armPx,
                            endX = (i + 1) * armPx,
                        ),
                        topLeft = Offset(i * armPx, -1f),
                        size = Size(armPx, size.height + 2f),
                    )
                }
            },
    ) {
        Box(
            Modifier
                .padding(start = m.arm * row.depth)
                .fillMaxWidth()
                .heightIn(min = if (row.kind == RowKind.RepeatEnd) m.endHeight else m.rowMinHeight)
                .graphicsLayer {
                    if (lifted) {
                        scaleX = 1.03f
                        scaleY = 1.03f
                        rotationZ = -1.2f
                    }
                }
                .drawBehind {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    if (lifted) {
                        drawOutline(outline, Color.Black.copy(alpha = 0.18f))
                    }
                    drawOutline(
                        outline,
                        Brush.verticalGradient(
                            listOf(look.color.lighten(0.14f), look.color, look.color.darken(0.08f)),
                            startY = 0f,
                            endY = size.height,
                        ),
                    )
                    // Candy-glass edge: bright on top, slightly darker underneath.
                    drawOutline(
                        outline,
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.55f),
                            0.3f to Color.White.copy(alpha = 0.0f),
                            0.85f to Color.Black.copy(alpha = 0.0f),
                            1f to Color.Black.copy(alpha = 0.18f),
                            startY = 0f,
                            endY = size.height,
                        ),
                        style = Stroke(1.4.dp.toPx()),
                    )
                    // Soft sheen across the top half.
                    drawOutline(
                        outline,
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.20f),
                            0.5f to Color.White.copy(alpha = 0f),
                            startY = 0f,
                            endY = size.height,
                        ),
                    )
                    val highlight = maxOf(selectAlpha, glow)
                    if (highlight > 0f) {
                        drawOutline(outline, Color.White.copy(alpha = 0.35f * highlight), style = Stroke(7.dp.toPx()))
                        drawOutline(outline, Color.White.copy(alpha = highlight), style = Stroke(2.5.dp.toPx()))
                    }
                }
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    haptics.tick()
                    actions.select(block.id)
                }
                .padding(
                    start = if (row.kind == RowKind.RepeatEnd) m.arm + 10.dp else 14.dp,
                    end = 10.dp,
                    top = m.notchDepth + 6.dp,
                    bottom = 8.dp,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (row.kind == RowKind.RepeatEnd) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Icon(Icons.Rounded.Replay, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                }
                return@Box
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BlockContent(block, board, look, actions)
                }
                if (issue != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(26.dp)
                            .background(if (issue.severity == Severity.Error) Color.White else Color.White.copy(alpha = 0.85f), CircleShape)
                            .clip(CircleShape)
                            .clickable { actions.showIssue(issue) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PriorityHigh,
                            issue.message,
                            tint = if (issue.severity == Severity.Error) Color(0xFFE5484D) else Color(0xFFE08A00),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockLabel(text: String) {
    Text(
        text,
        style = TfType.block,
        color = Color.White,
        modifier = Modifier.padding(vertical = 5.dp),
    )
}

@Composable
private fun BlockIcon(icon: ImageVector) {
    Box(Modifier.size(32.dp).background(Color.White.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun BlockContent(block: Block, board: Board, look: BlockLook, actions: BlockActions) {
    BlockIcon(look.icon)
    when (block) {
        is TapBlock -> {
            BlockLabel("tap")
            TargetPill(board, block.target, look.color) {
                actions.pickTarget(block.id, block.target, "Tap which point?") { id ->
                    actions.update(block.id) { (it as TapBlock).copy(target = id) }
                }
            }
            InputPill(
                when (block.count) { 1 -> "once"; 2 -> "twice"; else -> "${block.count}×" },
                look.color,
            ) {
                actions.editCount("How many taps in a row?", block.count, false) { n ->
                    actions.update(block.id) { (it as TapBlock).copy(count = n.coerceIn(1, 50)) }
                }
            }
        }
        is HoldBlock -> {
            BlockLabel("hold")
            TargetPill(board, block.target, look.color) {
                actions.pickTarget(block.id, block.target, "Hold which point?") { id ->
                    actions.update(block.id) { (it as HoldBlock).copy(target = id) }
                }
            }
            BlockLabel("for")
            InputPill(formatDurationShort(block.durationMs), look.color) {
                actions.editDuration("Hold for how long?", block.durationMs) { ms ->
                    actions.update(block.id) { (it as HoldBlock).copy(durationMs = ms.coerceAtLeast(1)) }
                }
            }
        }
        is SwipeBlock -> {
            BlockLabel("swipe")
            TargetPill(board, block.from, look.color) {
                actions.pickTarget(block.id, block.from, "Swipe from?") { id ->
                    actions.update(block.id) { (it as SwipeBlock).copy(from = id) }
                }
            }
            Icon(Icons.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.padding(top = 6.dp).size(20.dp))
            TargetPill(board, block.to, look.color) {
                actions.pickTarget(block.id, block.to, "Swipe to?") { id ->
                    actions.update(block.id) { (it as SwipeBlock).copy(to = id) }
                }
            }
            InputPill(formatDurationShort(block.durationMs), look.color) {
                actions.editDuration("Swipe duration", block.durationMs) { ms ->
                    actions.update(block.id) { (it as SwipeBlock).copy(durationMs = ms.coerceAtLeast(10)) }
                }
            }
        }
        is WaitBlock -> {
            BlockLabel("wait")
            InputPill(formatDurationShort(block.ms), look.color) {
                actions.editDuration("Wait how long?", block.ms) { ms ->
                    actions.update(block.id) { (it as WaitBlock).copy(ms = ms) }
                }
            }
        }
        is RandomWaitBlock -> {
            BlockLabel("wait")
            InputPill(formatDurationShort(block.minMs), look.color) {
                actions.editDuration("Shortest wait", block.minMs) { ms ->
                    actions.update(block.id) { (it as RandomWaitBlock).copy(minMs = ms) }
                }
            }
            BlockLabel("to")
            InputPill(formatDurationShort(block.maxMs), look.color) {
                actions.editDuration("Longest wait", block.maxMs) { ms ->
                    actions.update(block.id) { (it as RandomWaitBlock).copy(maxMs = ms) }
                }
            }
        }
        is RepeatBlock -> {
            BlockLabel("repeat")
            InputPill(
                if (block.forever) "forever" else "${block.times}×",
                look.color,
                icon = if (block.forever) Icons.Rounded.AllInclusive else null,
            ) {
                actions.editCount("Repeat how many times?", block.times, true) { n ->
                    actions.update(block.id) { (it as RepeatBlock).copy(times = n.coerceAtLeast(0)) }
                }
            }
        }
    }
}

/** A Scratch-style white input inside a block. */
@Composable
fun InputPill(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    dot: Color? = null,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animatedPressScale(pressed, 0.9f)
    val haptics = rememberHaptics()
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(32.dp)
            .background(Color.White, RoundedCornerShape(percent = 50))
            .clip(RoundedCornerShape(percent = 50))
            .clickable(source, indication = null, role = Role.Button) {
                haptics.tick()
                onClick()
            }
            .padding(start = if (dot != null) 6.dp else 12.dp, end = if (chevron) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(20.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        if (icon != null) {
            Icon(icon, null, tint = color.darken(0.3f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = TfType.label.copy(fontWeight = FontWeight.Bold), color = color.darken(0.35f), maxLines = 1)
        if (chevron) {
            Icon(Icons.Rounded.ExpandMore, null, tint = color.darken(0.3f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TargetPill(board: Board, targetId: String?, color: Color, onClick: () -> Unit) {
    val idx = board.indexOf(targetId)
    if (idx < 0) {
        Row(
            Modifier
                .height(32.dp)
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(percent = 50))
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onClick)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("choose", style = TfType.label.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Icon(Icons.Rounded.ExpandMore, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    } else {
        InputPill(board.targets[idx].name, color, dot = targetColor(idx), chevron = true, onClick = onClick)
    }
}

/** The green "when ▶ started" hat at the top of every chain, with the run count. */
@Composable
fun HatBlock(runs: RepeatPolicy, onRuns: () -> Unit, modifier: Modifier = Modifier) {
    val m = BlockMetrics
    val shape = remember { PuzzleShape(topNotchAt = null, bottomTabAt = m.notchX, hat = true) }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = m.rowMinHeight + m.hatCap)
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, Brush.verticalGradient(listOf(HatColor.lighten(0.14f), HatColor, HatColor.darken(0.08f))))
                drawOutline(
                    outline,
                    Brush.verticalGradient(0f to Color.White.copy(alpha = 0.5f), 0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.16f)),
                    style = Stroke(1.4.dp.toPx()),
                )
            }
            .padding(start = 14.dp, end = 10.dp, top = m.hatCap + 6.dp, bottom = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("when", style = TfType.block, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(30.dp).background(Color(0xFFFFD43B), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Flag, null, tint = HatColor.darken(0.25f), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("starts, run", style = TfType.block, color = Color.White, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            InputPill(
                when (runs.mode) {
                    CoreRepeatMode.Forever -> "forever"
                    CoreRepeatMode.Times -> if (runs.times <= 1) "once" else "${runs.times}×"
                    CoreRepeatMode.Duration -> formatDurationShort(runs.durationMs)
                },
                HatColor,
                icon = if (runs.mode == CoreRepeatMode.Forever) Icons.Rounded.AllInclusive else null,
                onClick = onRuns,
            )
        }
    }
}

/** Mini block used in the palette tray. */
@Composable
fun PaletteBlock(kind: com.tapflow.core.BlockKind, onClick: () -> Unit, height: Dp = 48.dp) {
    val look = lookOf(kind)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animatedPressScale(pressed, 0.9f)
    val haptics = rememberHaptics()
    val shape = remember { PuzzleShape(topNotchAt = 10.dp, bottomTabAt = 10.dp) }
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(height)
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, Brush.verticalGradient(listOf(look.color.lighten(0.14f), look.color, look.color.darken(0.08f))))
                drawOutline(
                    outline,
                    Brush.verticalGradient(0f to Color.White.copy(alpha = 0.55f), 0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.16f)),
                    style = Stroke(1.2.dp.toPx()),
                )
            }
            .clickable(source, indication = null, role = Role.Button) {
                haptics.confirm()
                onClick()
            }
            .padding(start = 12.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(look.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(look.label, style = TfType.block, color = Color.White, maxLines = 1)
    }
}
