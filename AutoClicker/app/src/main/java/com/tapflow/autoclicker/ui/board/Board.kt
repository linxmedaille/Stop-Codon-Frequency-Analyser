package com.tapflow.autoclicker.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapflow.autoclicker.data.ScreenshotCache
import com.tapflow.autoclicker.service.ClickerBridge
import com.tapflow.autoclicker.ui.glass.GlassDefaults
import com.tapflow.autoclicker.ui.glass.GlassIconButton
import com.tapflow.autoclicker.ui.glass.backdropSource
import com.tapflow.autoclicker.ui.glass.liquidGlass
import com.tapflow.autoclicker.ui.glass.rememberBackdrop
import com.tapflow.autoclicker.ui.glass.rememberHaptics
import com.tapflow.autoclicker.ui.glass.targetColor
import com.tapflow.autoclicker.ui.theme.Tf
import com.tapflow.autoclicker.ui.theme.TfType
import com.tapflow.core.Board
import com.tapflow.core.NPoint
import com.tapflow.core.Screenshot
import kotlin.math.max
import kotlin.math.roundToInt

/** Loads a screenshot bitmap off the main thread (cached). */
@Composable
fun rememberScreenshot(shot: Screenshot?): ImageBitmap? {
    val path = shot?.path
    val image by produceState(initialValue = path?.let { ScreenshotCache.get(it) }, path) {
        value = if (path == null) null else ScreenshotCache.load(path)
    }
    return image
}

/** Aspect (width / height) of what the board represents: the screenshot or this phone. */
@Composable
fun boardAspect(board: Board): Float {
    val context = LocalContext.current
    val shot = board.screenshot
    if (shot != null && shot.aspect > 0f) return shot.aspect
    val screen = remember { ClickerBridge.realScreen(context) }
    return screen.width.toFloat() / screen.height
}

/** Largest rect of [aspect] that fits inside [container], centred. */
fun fitRect(container: Size, aspect: Float): Rect {
    val cAspect = container.width / container.height
    return if (aspect > cAspect) {
        val h = container.width / aspect
        Rect(Offset(0f, (container.height - h) / 2f), Size(container.width, h))
    } else {
        val w = container.height * aspect
        Rect(Offset((container.width - w) / 2f, 0f), Size(w, container.height))
    }
}

/**
 * The rect a board fills when cropped into [container] (like ContentScale.Crop), shifted
 * so its targets stay in view.
 */
fun cropRect(container: Size, aspect: Float, board: Board): Rect {
    val scale = max(container.width / aspect, container.height)
    val w = scale * aspect
    val h = scale
    val targets = board.targets
    val focus = if (targets.isEmpty()) {
        NPoint(0.5f, 0.45f)
    } else {
        NPoint(targets.map { it.point.x }.average().toFloat(), targets.map { it.point.y }.average().toFloat())
    }
    val left = (container.width / 2f - focus.x * w).coerceIn(container.width - w, 0f)
    val top = (container.height / 2f - focus.y * h).coerceIn(container.height - h, 0f)
    return Rect(Offset(left, top), Size(w, h))
}

/** Draws a placeholder "phone screen" when there's no screenshot. */
fun DrawScope.drawMockScreen(rect: Rect, dark: Boolean) {
    drawRect(
        Brush.verticalGradient(
            if (dark) listOf(Color(0xFF1B1A22), Color(0xFF0F0F14)) else listOf(Color(0xFFFFFFFF), Color(0xFFEDE9F5)),
            startY = rect.top,
            endY = rect.bottom,
        ),
        topLeft = rect.topLeft,
        size = rect.size,
    )
    val line = if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
    val step = rect.width / 8f
    var x = rect.left + step
    while (x < rect.right) {
        drawLine(line, Offset(x, rect.top), Offset(x, rect.bottom), strokeWidth = 1f)
        x += step
    }
    var y = rect.top + step
    while (y < rect.bottom) {
        drawLine(line, Offset(rect.left, y), Offset(rect.right, y), strokeWidth = 1f)
        y += step
    }
}

/** A numbered target ring. */
fun DrawScope.drawTarget(
    center: Offset,
    index: Int,
    name: String,
    measurer: TextMeasurer,
    ring: Float,
    selected: Boolean = false,
    showLabel: Boolean = true,
) {
    val color = targetColor(index)
    drawCircle(Color.Black.copy(alpha = 0.22f), ring + 2.5f * density, center + Offset(0f, 1.5f * density))
    drawCircle(color.copy(alpha = if (selected) 0.45f else 0.28f), ring, center)
    drawCircle(Color.White, ring, center, style = Stroke((if (selected) 3f else 2f) * density))
    // Crosshair
    val arm = ring * 0.42f
    drawLine(Color.White.copy(alpha = 0.9f), center - Offset(arm, 0f), center - Offset(arm * 0.35f, 0f), 1.5f * density)
    drawLine(Color.White.copy(alpha = 0.9f), center + Offset(arm * 0.35f, 0f), center + Offset(arm, 0f), 1.5f * density)
    drawLine(Color.White.copy(alpha = 0.9f), center - Offset(0f, arm), center - Offset(0f, arm * 0.35f), 1.5f * density)
    drawLine(Color.White.copy(alpha = 0.9f), center + Offset(0f, arm * 0.35f), center + Offset(0f, arm), 1.5f * density)
    drawCircle(color, 3.5f * density, center)
    if (showLabel) {
        val label = measurer.measure(name, TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        val pad = Offset(7f * density, 3f * density)
        val tl = Offset(center.x - label.size.width / 2f, center.y + ring + 5f * density)
        drawRoundRect(
            color = color,
            topLeft = tl - pad,
            size = Size(label.size.width + pad.x * 2, label.size.height + pad.y * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(50f * density),
        )
        drawText(label, topLeft = tl)
    }
}

fun DrawScope.drawImageInto(image: ImageBitmap, rect: Rect) {
    drawImage(
        image,
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
    )
}

/**
 * The hero card on each mode page, styled like a profile photo: the screenshot (or a
 * placeholder) with the points on it, and glass buttons floating over the image.
 */
@Composable
fun BoardPreviewCard(
    board: Board,
    onEdit: () -> Unit,
    onPickScreenshot: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 0.86f,
    emptyHint: String = "Add a screenshot of the app you want to automate, then place your points on it.",
) {
    val c = Tf.colors
    val image = rememberScreenshot(board.screenshot)
    val aspect = boardAspect(board)
    val measurer = rememberTextMeasurer()
    val photoBackdrop = rememberBackdrop()
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .liquidGlass(null, shape, GlassDefaults.regular(c).copy(sheen = 0f), elevation = 14.dp, shadowColor = c.shadow)
            .clip(shape),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .backdropSource(photoBackdrop)
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    haptics.tap()
                    onEdit()
                },
        ) {
            if (image != null) {
                val rect = cropRect(size, aspect, board)
                drawImageInto(image, rect)
                board.targets.forEachIndexed { i, t ->
                    drawTarget(
                        Offset(rect.left + t.point.x * rect.width, rect.top + t.point.y * rect.height),
                        i, t.name, measurer, ring = 20.dp.toPx(),
                    )
                }
            } else {
                drawRect(
                    Brush.linearGradient(
                        if (c.isDark) listOf(Color(0xFF221C33), Color(0xFF14121C)) else listOf(Color(0xFFEDE5FF), Color(0xFFFFEFE6)),
                    ),
                )
                val inner = fitRect(Size(size.width, size.height * 0.84f), aspect).let {
                    Rect(Offset(it.left, it.top + size.height * 0.05f), it.size)
                }
                drawRoundRect(
                    Color.Black.copy(alpha = 0.10f),
                    topLeft = inner.topLeft + Offset(0f, 6.dp.toPx()),
                    size = inner.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(26.dp.toPx()),
                )
                clipPath(
                    androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(inner, androidx.compose.ui.geometry.CornerRadius(26.dp.toPx())))
                    },
                ) {
                    drawMockScreen(inner, c.isDark)
                }
                board.targets.forEachIndexed { i, t ->
                    drawTarget(
                        Offset(inner.left + t.point.x * inner.width, inner.top + t.point.y * inner.height),
                        i, t.name, measurer, ring = 15.dp.toPx(), showLabel = false,
                    )
                }
            }
        }

        if (image == null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Image, null, tint = c.inkTertiary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    emptyHint,
                    style = TfType.caption.copy(fontSize = TfType.label.fontSize * 0.92f),
                    color = c.inkSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        // Floating glass controls over the photo, like the round button on the reference.
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
                .liquidGlass(photoBackdrop, RoundedCornerShape(percent = 50), GlassDefaults.overPhoto(c), elevation = 8.dp, shadowColor = c.shadow)
                .clip(RoundedCornerShape(percent = 50))
                .clickable {
                    haptics.tap()
                    onPickScreenshot()
                }
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AddPhotoAlternate, null, tint = c.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (board.screenshot == null) "Add screenshot" else "Replace", style = TfType.label, color = c.ink)
        }
        GlassIconButton(
            icon = Icons.Rounded.CenterFocusStrong,
            contentDescription = "Edit points",
            onClick = onEdit,
            modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
            size = 58.dp,
            tint = Color.White,
            style = GlassDefaults.overPhoto(c).copy(tint = Color(0xD9161618), fallbackTint = Color(0xF0161618), rimLight = Color.White.copy(alpha = 0.4f)),
            backdrop = photoBackdrop,
        )
    }
}
