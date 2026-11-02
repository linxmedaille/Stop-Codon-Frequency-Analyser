package com.tapflow.autoclicker.ui.dock

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tapflow.autoclicker.ui.glass.Backdrop
import com.tapflow.autoclicker.ui.glass.GlassDefaults
import com.tapflow.autoclicker.ui.glass.GlassEffects
import com.tapflow.autoclicker.ui.glass.GlassStyle
import com.tapflow.autoclicker.ui.glass.drawGlassFinish
import com.tapflow.autoclicker.ui.glass.glassSupported
import com.tapflow.autoclicker.ui.glass.lensSupported
import com.tapflow.autoclicker.ui.glass.liquidGlass
import com.tapflow.autoclicker.ui.glass.liquidSpan
import com.tapflow.autoclicker.ui.glass.liquidStretch
import com.tapflow.autoclicker.ui.glass.rememberHaptics
import com.tapflow.autoclicker.ui.theme.Tf
import com.tapflow.autoclicker.ui.theme.TfType
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class DockItem(val label: String, val icon: ImageVector, val badge: String? = null)

/**
 * The floating dock. A glass capsule that refracts the page behind it; the selected tab
 * sits under a liquid lens that stretches as you swipe between pages, lifts and magnifies
 * while you're dragging, and can itself be dragged along the dock to scrub pages.
 */
@Composable
fun LiquidDock(
    items: List<DockItem>,
    pager: PagerState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val c = Tf.colors
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val shape = RoundedCornerShape(percent = 50)
    val contentLayer = rememberGraphicsLayer()
    var dockWidth by remember { mutableFloatStateOf(0f) }
    var touching by remember { mutableStateOf(false) }

    val position by remember {
        derivedStateOf { pager.currentPage + pager.currentPageOffsetFraction }
    }
    val lifted by remember { derivedStateOf { touching || pager.isScrollInProgress } }
    val lift by animateFloatAsState(
        targetValue = if (lifted) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "dock-lift",
    )

    val dockHeight = 74.dp
    val indicatorStyle = remember(c) {
        GlassDefaults.clear(c).copy(
            tint = Color.Transparent,
            blur = 0.dp,
            refraction = 1f,
            bezel = 14.dp,
            saturation = 1.6f,
        )
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(dockHeight)
            .onGloballyPositioned { dockWidth = it.size.width.toFloat() }
            .liquidGlass(backdrop, shape, GlassDefaults.regular(c).copy(blur = 18.dp, refraction = 0.75f, bezel = 22.dp), elevation = 22.dp, shadowColor = c.shadow)
            .pointerInput(items.size) {
                // Tap a tab, or press-and-drag the lens across the dock to scrub pages.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touching = true
                    val slot = size.width / items.size.toFloat()
                    var dragged = false
                    var totalDx = 0f
                    val pageWidth = pager.layoutInfo.pageSize.toFloat().coerceAtLeast(1f)
                    var lastTick = position.roundToInt()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.positionChange().x
                        totalDx += dx
                        if (!dragged && abs(totalDx) > viewConfiguration.touchSlop) dragged = true
                        if (dragged && dx != 0f) {
                            change.consume()
                            pager.dispatchRawDelta(-dx * pageWidth / slot)
                            val now = (pager.currentPage + pager.currentPageOffsetFraction).roundToInt()
                            if (now != lastTick) {
                                lastTick = now
                                haptics.tick()
                            }
                        }
                    }
                    touching = false
                    val target = if (dragged) {
                        (pager.currentPage + pager.currentPageOffsetFraction).roundToInt()
                    } else {
                        (down.position.x / slot).toInt()
                    }.coerceIn(0, items.lastIndex)
                    if (!dragged) haptics.tick()
                    scope.launch {
                        pager.animateScrollToPage(
                            target,
                            animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
                        )
                    }
                }
            },
    ) {
        // Tab contents, recorded so the lens can magnify them.
        Row(
            Modifier
                .fillMaxWidth()
                .height(dockHeight)
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(contentLayer)
                },
        ) {
            items.forEachIndexed { i, item ->
                val active = (1f - abs(position - i)).coerceIn(0f, 1f)
                DockTab(
                    item = item,
                    active = active,
                    selected = pager.currentPage == i,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // The lens.
        if (dockWidth > 0f) {
            val slot = dockWidth / items.size
            val inset = with(density) { 5.dp.toPx() }
            val (l, r) = liquidSpan(position, slot, inset, items.size)
            val stretch = liquidStretch(position)
            val widthPx = r - l
            val heightPx = with(density) { (dockHeight - 10.dp).toPx() }
            val fx = remember { GlassEffects() }
            Box(
                Modifier
                    .offset { IntOffset(l.roundToInt(), with(density) { 5.dp.roundToPx() }) }
                    .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                    .graphicsLayer {
                        val s = 1f + 0.16f * lift
                        scaleX = s * (1f + 0.04f * stretch)
                        scaleY = s * (1f - 0.10f * stretch)
                        shadowElevation = (2f + 14f * lift) * density.density
                        this.shape = RoundedCornerShape(percent = 50)
                        clip = false
                        ambientShadowColor = c.shadow
                        spotShadowColor = c.shadow
                    }
                    .drawBehind {
                        val outline = Outline.Rounded(
                            androidx.compose.ui.geometry.RoundRect(
                                0f, 0f, size.width, size.height,
                                androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                            ),
                        )
                        // Liquid body: base tint that strengthens when lifted.
                        val base = if (c.isDark) Color.White.copy(alpha = 0.12f + 0.06f * lift) else Color.Black.copy(alpha = 0.085f * (1f - lift) + 0.025f)
                        drawOutline(outline, base)
                    }
                    .then(
                        Modifier.lensOf(
                            source = contentLayer,
                            offset = Offset(l, with(density) { 5.dp.toPx() }),
                            zoom = 1f + 0.18f * lift,
                            effects = fx,
                            style = indicatorStyle,
                            lift = lift,
                            lightRim = true,
                        ),
                    ),
            )
        }
    }
}

/**
 * Draws [source] (the dock's tab row) inside this element through the lens shader:
 * magnified by [zoom] and bent at the rim. Below Android 13 it's just magnified.
 */
private fun Modifier.lensOf(
    source: androidx.compose.ui.graphics.layer.GraphicsLayer,
    offset: Offset,
    zoom: Float,
    effects: GlassEffects,
    style: GlassStyle,
    lift: Float,
    lightRim: Boolean,
): Modifier = this
    .graphicsLayer {
        clip = true
        this.shape = RoundedCornerShape(percent = 50)
        if (glassSupported) {
            val outline = Outline.Rounded(
                androidx.compose.ui.geometry.RoundRect(
                    0f, 0f, size.width, size.height,
                    androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                ),
            )
            renderEffect = effects.effectFor(this, Size(size.width, size.height), outline, style.copy(zoom = zoom))
        }
    }
    .drawBehind {
        val outline = Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(
                0f, 0f, size.width, size.height,
                androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            ),
        )
        if (lift > 0.01f) {
            drawOutline(outline, Color.White.copy(alpha = 0.18f * lift))
        }
        if (lensSupported) {
            // The lens shader does the magnifying.
            translate(-offset.x, -offset.y) { drawLayer(source) }
        } else {
            val pivot = Offset(size.width / 2f, size.height / 2f)
            scale(zoom, pivot) {
                translate(-offset.x, -offset.y) { drawLayer(source) }
            }
        }
        if (lightRim) {
            drawGlassFinish(outline, style.copy(sheen = 0.18f * lift, rimLight = Color.White.copy(alpha = 0.55f + 0.45f * lift)))
        }
    }

@Composable
private fun DockTab(item: DockItem, active: Float, selected: Boolean, modifier: Modifier) {
    val c = Tf.colors
    val tint = lerp(c.inkSecondary, c.ink, active)
    Box(
        modifier
            .fillMaxHeight()
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = item.label
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Icon(item.icon, null, tint = tint, modifier = Modifier.size(26.dp))
                if (item.badge != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 9.dp, y = (-7).dp)
                            .size(18.dp)
                            .background(c.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.badge, style = TfType.caption.copy(fontWeight = FontWeight.Bold), color = c.onAccent)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                item.label,
                style = TfType.caption.copy(fontSize = TfType.label.fontSize * 0.9f, fontWeight = if (active > 0.5f) FontWeight.SemiBold else FontWeight.Medium),
                color = tint,
                maxLines = 1,
            )
        }
    }
}
