package com.tapflow.autoclicker.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.tapflow.autoclicker.ui.theme.Tf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The living background glass refracts: a few huge soft colour blobs drifting slowly.
 * [pagePosition] (current page + swipe fraction) shifts the palette and adds parallax so
 * swiping between pages feels like the light moves with you.
 */
@Composable
fun Aurora(
    pagePosition: () -> Float,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val c = Tf.colors
    val t: State<Float> = if (animate) {
        val transition = rememberInfiniteTransition(label = "aurora")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
            label = "aurora-t",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.15f) }
    }
    val blobs = remember { BLOBS }
    Canvas(modifier) {
        drawRect(c.base)
        val pos = pagePosition()
        val i = floor(pos).toInt()
        val f = pos - i
        val palettes = c.aurora
        val from = palettes[i.coerceIn(0, palettes.lastIndex)]
        val to = palettes[(i + 1).coerceIn(0, palettes.lastIndex)]
        val time = t.value * 2f * PI.toFloat()
        val w = size.width
        val h = size.height
        val alpha = if (c.isDark) 0.62f else 0.9f
        blobs.forEachIndexed { index, b ->
            val color = lerp(from[index % from.size], to[index % to.size], f)
            val cx = w * (b.x + b.ax * sin(time * b.speed + b.phase)) - pos * w * b.parallax
            val cy = h * (b.y + b.ay * cos(time * b.speed * 0.8f + b.phase))
            val r = b.r * maxOf(w, h) * (1f + 0.06f * sin(time * 2f + b.phase))
            // Wrap the parallax so a blob never leaves for good.
            val span = w * (1f + 2f * b.r)
            val wrapped = ((cx + w * b.r) % span + span) % span - w * b.r
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = alpha),
                    0.55f to color.copy(alpha = alpha * 0.45f),
                    1f to Color.Transparent,
                    center = Offset(wrapped, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(wrapped, cy),
            )
        }
    }
}

private class Blob(
    val x: Float,
    val y: Float,
    val r: Float,
    val ax: Float,
    val ay: Float,
    val speed: Float,
    val phase: Float,
    val parallax: Float,
)

private val BLOBS = listOf(
    Blob(x = 0.15f, y = 0.12f, r = 0.55f, ax = 0.10f, ay = 0.05f, speed = 1f, phase = 0f, parallax = 0.18f),
    Blob(x = 0.95f, y = 0.30f, r = 0.50f, ax = 0.08f, ay = 0.07f, speed = 1f, phase = 1.9f, parallax = 0.32f),
    Blob(x = 0.25f, y = 0.72f, r = 0.52f, ax = 0.09f, ay = 0.06f, speed = 2f, phase = 3.7f, parallax = 0.24f),
    Blob(x = 0.85f, y = 0.95f, r = 0.48f, ax = 0.07f, ay = 0.04f, speed = 1f, phase = 5.1f, parallax = 0.40f),
)
