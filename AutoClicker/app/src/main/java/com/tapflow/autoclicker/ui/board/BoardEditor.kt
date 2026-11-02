package com.tapflow.autoclicker.ui.board

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tapflow.autoclicker.service.ClickerBridge
import com.tapflow.autoclicker.ui.glass.GlassDefaults
import com.tapflow.autoclicker.ui.glass.GlassIconButton
import com.tapflow.autoclicker.ui.glass.GlassSurface
import com.tapflow.autoclicker.ui.glass.TargetDot
import com.tapflow.autoclicker.ui.glass.backdropSource
import com.tapflow.autoclicker.ui.glass.rememberBackdrop
import com.tapflow.autoclicker.ui.glass.rememberHaptics
import com.tapflow.autoclicker.ui.theme.Tf
import com.tapflow.autoclicker.ui.theme.TfType
import com.tapflow.core.Board
import com.tapflow.core.Mode
import com.tapflow.core.NPoint
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Full-screen point editor. The screenshot is shown edge to edge, at the same size as the
 * real screen when it came from this phone, so a point placed here is exactly where the
 * tap will land.
 */
@Composable
fun BoardEditor(
    mode: Mode,
    board: Board,
    onBoardChange: ((Board) -> Board) -> Unit,
    onRemoveTarget: (String) -> Unit,
    onPickScreenshot: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Tf.colors
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val image = rememberScreenshot(board.screenshot)
    val aspect = boardAspect(board)
    val measurer = rememberTextMeasurer()
    val backdrop = rememberBackdrop()
    val currentBoard by rememberUpdatedState(board)
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var selected by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var touched by remember { mutableStateOf(false) }
    val single = mode == Mode.Single
    val screen = remember { ClickerBridge.realScreen(context) }

    BackHandler(onBack = onClose)
    ImmersiveWhileShown()

    val rect = if (canvasSize == Size.Zero) Rect.Zero else fitRect(canvasSize, aspect)
    // Gesture handlers outlive a composition, so they read the latest rect through state.
    val liveRect by rememberUpdatedState(rect)
    fun toNorm(p: Offset): NPoint {
        val r = liveRect
        return NPoint((p.x - r.left) / r.width, (p.y - r.top) / r.height).clamped()
    }
    fun toPx(n: NPoint): Offset {
        val r = liveRect
        return Offset(r.left + n.x * r.width, r.top + n.y * r.height)
    }

    val loupeAlpha by animateFloatAsState(if (dragging != null) 1f else 0f, spring(0.8f, 600f), label = "loupe")

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .backdropSource(backdrop)
                .pointerInput(single) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        touched = true
                        val b = currentBoard
                        val hitRadius = 34.dp.toPx()
                        val hit = b.targets
                            .map { it to toPx(it.point) }
                            .filter { (_, p) -> hypot(p.x - down.position.x, p.y - down.position.y) < hitRadius }
                            .minByOrNull { (_, p) -> hypot(p.x - down.position.x, p.y - down.position.y) }
                        val grab = hit?.let { it.second - down.position } ?: Offset.Zero
                        var moved = false
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            last = change.position
                            if (!moved && (last - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                                if (hit != null) {
                                    dragging = hit.first.id
                                    selected = hit.first.id
                                    haptics.tick()
                                }
                            }
                            if (moved && hit != null) {
                                change.consume()
                                finger = last + grab
                                val id = hit.first.id
                                onBoardChange { it.moveTarget(id, toNorm(last + grab)) }
                            }
                        }
                        if (dragging != null) haptics.tap()
                        dragging = null
                        if (!moved) {
                            when {
                                hit != null -> {
                                    haptics.tick()
                                    selected = if (selected == hit.first.id) null else hit.first.id
                                }
                                !liveRect.contains(down.position) -> selected = null
                                single && b.targets.isNotEmpty() -> {
                                    haptics.tap()
                                    val id = b.targets.first().id
                                    onBoardChange { it.moveTarget(id, toNorm(down.position)) }
                                    selected = id
                                }
                                else -> {
                                    haptics.confirm()
                                    var newId: String? = null
                                    onBoardChange { board ->
                                        val (nb, t) = board.addTarget(toNorm(down.position))
                                        newId = t.id
                                        nb
                                    }
                                    selected = newId
                                }
                            }
                        }
                    }
                },
        ) {
            if (rect == Rect.Zero) return@Canvas
            if (image != null) {
                drawImageInto(image, rect)
            } else {
                drawMockScreen(rect, dark = true)
            }
            board.targets.forEachIndexed { i, t ->
                drawTarget(toPx(t.point), i, t.name, measurer, ring = 22.dp.toPx(), selected = t.id == selected)
            }
        }

        // Magnifying loupe above the finger while dragging, so the finger never hides the spot.
        if (loupeAlpha > 0.01f && image != null && rect != Rect.Zero) {
            Canvas(Modifier.fillMaxSize()) {
                val r = 58.dp.toPx()
                val gap = 96.dp.toPx()
                val center = Offset(
                    finger.x.coerceIn(r + 8f, size.width - r - 8f),
                    if (finger.y - gap - r > 0) finger.y - gap else finger.y + gap,
                )
                val zoom = 2.6f
                val scale = rect.width / image.width
                val srcSide = (r * 2) / (scale * zoom)
                val srcCx = (finger.x - rect.left) / scale
                val srcCy = (finger.y - rect.top) / scale
                val path = Path().apply { addOval(Rect(center, r)) }
                drawCircle(Color.Black.copy(alpha = 0.35f * loupeAlpha), r + 6.dp.toPx(), center + Offset(0f, 4.dp.toPx()))
                clipPath(path) {
                    drawRect(Color.Black, topLeft = center - Offset(r, r), size = Size(r * 2, r * 2), alpha = loupeAlpha)
                    drawImage(
                        image,
                        srcOffset = IntOffset((srcCx - srcSide / 2).roundToInt(), (srcCy - srcSide / 2).roundToInt()),
                        srcSize = IntSize(srcSide.roundToInt().coerceAtLeast(1), srcSide.roundToInt().coerceAtLeast(1)),
                        dstOffset = IntOffset((center.x - r).roundToInt(), (center.y - r).roundToInt()),
                        dstSize = IntSize((r * 2).roundToInt(), (r * 2).roundToInt()),
                        alpha = loupeAlpha,
                    )
                    drawCircle(Color.White.copy(alpha = 0.22f * loupeAlpha), r, center - Offset(0f, r * 0.9f))
                }
                val arm = 12.dp.toPx()
                drawLine(Color.White.copy(alpha = loupeAlpha), center - Offset(arm, 0f), center + Offset(arm, 0f), 2.dp.toPx())
                drawLine(Color.White.copy(alpha = loupeAlpha), center - Offset(0f, arm), center + Offset(0f, arm), 2.dp.toPx())
                drawCircle(Color.White.copy(alpha = 0.95f * loupeAlpha), r, center, style = Stroke(3.dp.toPx()))
            }
        }

        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.Rounded.Close, "Close", onClose, backdrop = backdrop, style = GlassDefaults.overPhoto(c))
            Spacer(Modifier.width(10.dp))
            GlassSurface(
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(percent = 50),
                style = GlassDefaults.overPhoto(c),
                backdrop = backdrop,
                elevation = 8.dp,
            ) {
                Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 18.dp)) {
                    Text(
                        when (mode) {
                            Mode.Single -> "Tap point"
                            Mode.Multi -> "Tap points · ${board.targets.size}"
                            Mode.Chain -> "Chain points · ${board.targets.size}"
                        },
                        style = TfType.label,
                        color = c.ink,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            GlassIconButton(Icons.Rounded.AddPhotoAlternate, "Replace screenshot", onPickScreenshot, backdrop = backdrop, style = GlassDefaults.overPhoto(c))
            Spacer(Modifier.width(10.dp))
            GlassIconButton(
                Icons.Rounded.Check, "Done", onClose,
                backdrop = null,
                tint = c.onAccent,
                style = GlassDefaults.overPhoto(c).copy(tint = c.accent, fallbackTint = c.accent),
            )
        }

        // Bottom bar: hint, or the selected point's details.
        val selectedTarget = board.targets.firstOrNull { it.id == selected }
        val selectedIndex = board.targets.indexOfFirst { it.id == selected }
        AnimatedContent(
            targetState = selectedTarget?.id to touched,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            transitionSpec = {
                (slideInVertically(spring(0.8f, 500f)) { it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut())
            },
            label = "editor-bottom",
        ) { (id, _) ->
            val t = board.targets.firstOrNull { it.id == id }
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                style = GlassDefaults.overPhoto(c),
                backdrop = backdrop,
                elevation = 12.dp,
            ) {
                if (t != null) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TargetDot(selectedIndex, size = 38.dp, textStyle = TfType.label)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = TfType.title, color = c.ink)
                            Text(
                                "x ${(t.point.x * screen.width).roundToInt()} · y ${(t.point.y * screen.height).roundToInt()} px",
                                style = TfType.caption,
                                color = c.inkSecondary,
                            )
                        }
                        if (!single) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(c.danger.copy(alpha = 0.14f))
                                    .clickable {
                                        haptics.heavy()
                                        selected = null
                                        onRemoveTarget(t.id)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, null, tint = c.danger, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Delete", style = TfType.label, color = c.danger)
                            }
                        }
                    }
                } else {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TouchApp, null, tint = c.accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            when {
                                single && board.targets.isEmpty() -> "Tap where the click should land"
                                single -> "Tap or drag to move the point"
                                board.targets.isEmpty() -> "Tap to add points, in order"
                                else -> "Tap to add · drag to move · tap a point for options"
                            },
                            style = TfType.label,
                            color = c.ink,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!single && board.targets.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .clickable {
                                        haptics.heavy()
                                        board.targets.forEach { onRemoveTarget(it.id) }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Rounded.LayersClear, null, tint = c.inkSecondary, modifier = Modifier.size(18.dp))
                                Text("Clear", style = TfType.label, color = c.inkSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Hides the system bars while the editor is open so the screenshot maps 1:1. */
@Composable
private fun ImmersiveWhileShown() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
