package com.tapflow.autoclicker.ui.chain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tapflow.core.BlockKind

/** Geometry shared by every block so their notches line up. */
object BlockMetrics {
    val arm: Dp = 18.dp
    val notchX: Dp = 16.dp
    val notchW: Dp = 32.dp
    val notchDepth: Dp = 7.dp
    val radius: Dp = 12.dp
    val rowMinHeight: Dp = 58.dp
    val endHeight: Dp = 26.dp
    val hatCap: Dp = 16.dp
}

data class BlockLook(val color: Color, val icon: ImageVector, val label: String, val description: String)

fun lookOf(kind: BlockKind): BlockLook = when (kind) {
    BlockKind.Tap -> BlockLook(Color(0xFF5468FF), Icons.Rounded.TouchApp, "Tap", "Tap a point")
    BlockKind.Hold -> BlockLook(Color(0xFF9457F5), Icons.Rounded.BackHand, "Hold", "Press and hold")
    BlockKind.Swipe -> BlockLook(Color(0xFF10AFA0), Icons.Rounded.SwipeRight, "Swipe", "Swipe between points")
    BlockKind.Wait -> BlockLook(Color(0xFFF2A007), Icons.Rounded.Schedule, "Wait", "Pause for a while")
    BlockKind.RandomWait -> BlockLook(Color(0xFFF3780F), Icons.Rounded.Shuffle, "Wait random", "Pause a random time")
    BlockKind.Repeat -> BlockLook(Color(0xFFEB4F6B), Icons.Rounded.Repeat, "Repeat", "Loop the blocks inside")
}

val HatColor = Color(0xFF22B45E)

/**
 * The jigsaw outline of a block: a notch cut into the top edge and a tab sticking out of
 * the bottom edge (below the bounds, into the next block's notch).
 *
 * @param topNotchAt x of the top notch, or null for none.
 * @param bottomTabAt x of the bottom tab, or null for none.
 * @param squareTopLeft / squareBottomLeft keep that corner square where a repeat's arm joins.
 * @param hat draw a rounded "hat" bump on top, for the start block.
 */
class PuzzleShape(
    private val topNotchAt: Dp?,
    private val bottomTabAt: Dp?,
    private val squareTopLeft: Boolean = false,
    private val squareBottomLeft: Boolean = false,
    private val hat: Boolean = false,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        with(density) {
            val w = size.width
            val h = size.height
            val r = BlockMetrics.radius.toPx().coerceAtMost(minOf(w, h) / 2f)
            val d = BlockMetrics.notchDepth.toPx()
            val nw = BlockMetrics.notchW.toPx()
            val path = Path()
            val top = if (hat) BlockMetrics.hatCap.toPx() else 0f

            if (hat) {
                val capW = minOf(w * 0.42f, 132.dp.toPx())
                path.moveTo(0f, top + r * 0.5f)
                path.cubicTo(0f, top * 0.2f, capW * 0.2f, 0f, capW * 0.5f, 0f)
                path.cubicTo(capW * 0.8f, 0f, capW, top * 0.3f, capW + 8.dp.toPx(), top)
                path.lineTo(w - r, top)
            } else {
                if (squareTopLeft) path.moveTo(0f, 0f) else {
                    path.moveTo(0f, r)
                    path.quadraticTo(0f, 0f, r, 0f)
                }
                topNotchAt?.let {
                    val nx = it.toPx()
                    path.lineTo(nx, 0f)
                    path.lineTo(nx + d, d)
                    path.lineTo(nx + nw - d, d)
                    path.lineTo(nx + nw, 0f)
                }
                path.lineTo(w - r, 0f)
            }
            path.quadraticTo(w, top, w, top + r)
            path.lineTo(w, h - r)
            path.quadraticTo(w, h, w - r, h)
            bottomTabAt?.let {
                val tx = it.toPx()
                path.lineTo(tx + nw, h)
                path.lineTo(tx + nw - d, h + d)
                path.lineTo(tx + d, h + d)
                path.lineTo(tx, h)
            }
            if (squareBottomLeft) {
                path.lineTo(0f, h)
            } else {
                path.lineTo(r, h)
                path.quadraticTo(0f, h, 0f, h - r)
            }
            path.close()
            Outline.Generic(path)
        }
}
