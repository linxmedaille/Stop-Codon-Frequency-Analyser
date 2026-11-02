package com.tapflow.autoclicker.service

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.autoclicker.TapflowApp
import com.tapflow.autoclicker.ui.glass.GlassStyle
import com.tapflow.autoclicker.ui.glass.liquidGlass
import com.tapflow.autoclicker.ui.glass.rememberHaptics
import com.tapflow.autoclicker.ui.glass.targetColor
import com.tapflow.autoclicker.ui.theme.TapflowTheme
import com.tapflow.autoclicker.ui.theme.TfType
import com.tapflow.core.FinishReason
import com.tapflow.core.Marker
import com.tapflow.core.formatCount
import kotlinx.coroutines.flow.first

/** The floating control capsule shown over other apps. */
@Composable
fun OverlayPanel(
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onToggleMarkers: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    TapflowTheme(dark = true) {
        val status by ClickerBridge.status.collectAsStateWithLifecycle()
        val ws by TapflowApp.store.workspace.collectAsStateWithLifecycle()
        var expanded by remember { mutableStateOf(false) }
        val haptics = rememberHaptics()
        val shape = RoundedCornerShape(34.dp)
        Column(
            Modifier
                .padding(10.dp)
                .liquidGlass(
                    backdrop = null,
                    shape = shape,
                    style = GlassStyle(
                        tint = Color(0xE6141418),
                        fallbackTint = Color(0xE6141418),
                        rimLight = Color.White.copy(alpha = 0.45f),
                        rimDark = Color.White.copy(alpha = 0.06f),
                        sheen = 0.10f,
                    ),
                    elevation = 16.dp,
                    shadowColor = Color.Black.copy(alpha = 0.6f),
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onDrag(drag.x, drag.y)
                    }
                }
                .padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Grip()
                Spacer(Modifier.width(6.dp))
                // Main button: play / pause / countdown.
                val accent = Brush.verticalGradient(listOf(Color(0xFFB993FF), Color(0xFF7B4DF0)))
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            haptics.confirm()
                            when (status.phase) {
                                Phase.Running -> onPause()
                                Phase.Countdown -> onStop()
                                else -> onPlay()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = status.phase to status.countdown,
                        transitionSpec = {
                            (scaleIn(spring(0.6f, 500f), 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 1.3f) + fadeOut())
                        },
                        label = "main",
                    ) { (phase, n) ->
                        when (phase) {
                            Phase.Countdown -> Text("$n", style = TfType.title.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = Color.White)
                            Phase.Running -> Icon(Icons.Rounded.Pause, "Pause", tint = Color.White, modifier = Modifier.size(28.dp))
                            Phase.Finished -> Icon(Icons.Rounded.Replay, "Run again", tint = Color.White, modifier = Modifier.size(26.dp))
                            else -> Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                PanelButton(Icons.Rounded.Stop, "Stop", enabled = status.active, size = 44.dp) {
                    haptics.heavy()
                    onStop()
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.widthIn(min = 108.dp, max = 168.dp)) {
                    Text(
                        status.title.ifEmpty { "Tapflow" },
                        style = TfType.label.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        subtitle(status),
                        style = TfType.caption,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(4.dp))
                PanelButton(Icons.Rounded.MoreHoriz, "More", size = 40.dp, filled = false) {
                    haptics.tick()
                    expanded = !expanded
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(0.8f, 600f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    Modifier.padding(top = 8.dp, start = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PanelButton(
                        if (ws.prefs.showMarkers) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        "Toggle markers",
                        size = 40.dp,
                    ) { onToggleMarkers() }
                    PanelButton(Icons.Rounded.OpenInNew, "Open Tapflow", size = 40.dp) { onOpenApp() }
                    PanelButton(Icons.Rounded.Close, "Close panel", size = 40.dp) {
                        haptics.heavy()
                        onClose()
                    }
                    Text(
                        if (ws.prefs.volumeKeyStop) "Vol − stops" else "",
                        style = TfType.caption,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

private fun subtitle(s: RunStatus): String {
    val time = formatClock(s.elapsedMs)
    val loops = if (s.loopTimes > 0) " · ${s.loop}/${s.loopTimes}" else if (s.loop > 1) " · loop ${s.loop}" else ""
    return when (s.phase) {
        Phase.Idle, Phase.Armed -> "Press ▶ when ready"
        Phase.Countdown -> "Starting in ${s.countdown}…"
        Phase.Running -> "${formatCount(s.gestures)} taps · $time$loops"
        Phase.Paused -> "Paused · ${formatCount(s.gestures)} taps"
        Phase.Finished -> s.message ?: when (s.finishReason) {
            FinishReason.Stopped -> "Stopped · ${formatCount(s.gestures)} taps"
            FinishReason.TimeLimit -> "Time's up · ${formatCount(s.gestures)} taps"
            else -> "Done · ${formatCount(s.gestures)} taps · $time"
        }
    }
}

fun formatClock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun Grip() {
    Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { Box(Modifier.size(4.dp).background(Color.White.copy(alpha = 0.35f), CircleShape)) }
            }
        }
    }
}

@Composable
private fun PanelButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    enabled: Boolean = true,
    filled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) Color.White.copy(alpha = if (enabled) 0.12f else 0.05f) else Color.Transparent)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White.copy(alpha = if (enabled) 0.95f else 0.3f), modifier = Modifier.size(size * 0.5f))
    }
}

private class Flash(val at: Offset, val to: Offset?, val born: Long)

/** Full-screen, touch-through layer: numbered rings where taps land, plus tap ripples. */
@Composable
fun MarkersLayer(markers: List<Marker>) {
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val flashes = remember { mutableStateListOf<Flash>() }
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    val measurer = rememberTextMeasurer()

    LaunchedEffect(Unit) {
        ClickerBridge.flashes.collect { f ->
            flashes.add(
                Flash(
                    Offset(f.at.x, f.at.y),
                    f.to?.let { Offset(it.x, it.y) },
                    System.nanoTime(),
                ),
            )
            while (flashes.size > 24) flashes.removeAt(0)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            if (flashes.isEmpty()) snapshotFlow { flashes.size }.first { it > 0 }
            withFrameNanos { now = System.nanoTime() }
            flashes.removeAll { now - it.born > 650_000_000L }
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                val loc = IntArray(2)
                view.getLocationOnScreen(loc)
                origin = Offset(loc[0].toFloat(), loc[1].toFloat())
            },
    ) {
        val ring = 22.dp.toPx()
        markers.forEachIndexed { i, m ->
            val center = Offset(m.at.x, m.at.y) - origin
            val color = targetColor(i)
            drawCircle(Color.Black.copy(alpha = 0.18f), ring + 3.dp.toPx(), center)
            drawCircle(color.copy(alpha = 0.28f), ring, center)
            drawCircle(Color.White.copy(alpha = 0.9f), ring, center, style = Stroke(2.dp.toPx()))
            drawCircle(color, 5.dp.toPx(), center)
            drawCircle(Color.White, 5.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
            val label = measurer.measure(m.name, TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            val tl = Offset(center.x - label.size.width / 2f, center.y + ring + 6.dp.toPx())
            drawRoundRect(
                color = color,
                topLeft = tl - Offset(7.dp.toPx(), 3.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(label.size.width + 14.dp.toPx(), label.size.height + 6.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
            )
            drawText(label, topLeft = tl)
        }
        for (f in flashes) {
            val t = ((now - f.born) / 650_000_000f).coerceIn(0f, 1f)
            val alpha = (1f - t) * (1f - t)
            val start = f.at - origin
            if (f.to != null) {
                val end = f.to - origin
                drawLine(Color.White.copy(alpha = 0.8f * alpha), start, end, strokeWidth = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = alpha), 8.dp.toPx(), end)
            }
            drawCircle(Color.White.copy(alpha = 0.55f * alpha), 8.dp.toPx() + 30.dp.toPx() * t, start)
            drawCircle(Color.White.copy(alpha = alpha), 8.dp.toPx() + 30.dp.toPx() * t, start, style = Stroke(2.dp.toPx()))
        }
    }
}
