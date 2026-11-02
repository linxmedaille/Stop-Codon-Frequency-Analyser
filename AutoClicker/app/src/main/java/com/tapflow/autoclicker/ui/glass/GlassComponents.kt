package com.tapflow.autoclicker.ui.glass

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.tapflow.autoclicker.ui.theme.Tf
import com.tapflow.autoclicker.ui.theme.TfType
import kotlin.math.floor
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------
// Haptics
// ---------------------------------------------------------------------------------------

@Stable
class Haptics(private val view: android.view.View, private val enabled: () -> Boolean) {
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)
    fun tap() = perform(HapticFeedbackConstants.VIRTUAL_KEY)
    fun heavy() = perform(HapticFeedbackConstants.LONG_PRESS)
    fun confirm() = perform(
        if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
    )
    fun reject() = perform(
        if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
    )

    private fun perform(type: Int) {
        if (enabled()) view.performHapticFeedback(type)
    }
}

val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled by rememberUpdatedState(LocalHapticsEnabled.current)
    return remember(view) { Haptics(view) { enabled } }
}

// ---------------------------------------------------------------------------------------
// Press feedback: glass squishes a little when pressed, then springs back.
// ---------------------------------------------------------------------------------------

@Composable
fun animatedPressScale(pressed: Boolean, amount: Float = 0.95f): Float {
    val s by animateFloatAsState(
        targetValue = if (pressed) amount else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = if (pressed) 900f else 380f),
        label = "press",
    )
    return s
}

// ---------------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------------

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    style: GlassStyle = GlassDefaults.regular(Tf.colors),
    backdrop: Backdrop? = LocalBackdrop.current,
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.liquidGlass(backdrop, shape, style, elevation, Tf.colors.shadow),
        content = content,
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    shape: Shape = RoundedCornerShape(30.dp),
    style: GlassStyle = GlassDefaults.regular(Tf.colors),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = shape, style = style) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** Small uppercase label above card content. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Tf.colors.inkTertiary) {
    Text(text.uppercase(), style = TfType.kicker, color = color, modifier = modifier)
}

// ---------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    tint: Color = Tf.colors.ink,
    style: GlassStyle = GlassDefaults.regular(Tf.colors),
    backdrop: Backdrop? = LocalBackdrop.current,
    elevation: Dp = 8.dp,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animatedPressScale(pressed, 0.88f)
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .liquidGlass(backdrop, CircleShape, style, elevation, Tf.colors.shadow)
            .clip(CircleShape)
            .clickable(source, indication = null, role = Role.Button) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/** The big call-to-action: a candy-glass pill in the accent colour. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Tf.colors.accent,
    contentColor: Color = Tf.colors.onAccent,
    enabled: Boolean = true,
    height: Dp = 60.dp,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animatedPressScale(pressed, 0.96f)
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(percent = 50)
    val c = Tf.colors
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .liquidGlass(
                backdrop = null,
                shape = shape,
                style = GlassStyle(
                    tint = color,
                    fallbackTint = color,
                    rimLight = Color.White.copy(alpha = if (c.isDark) 0.55f else 0.6f),
                    rimDark = Color.White.copy(alpha = 0.08f),
                    sheen = 0.28f,
                    rimWidth = 1.2.dp,
                ),
                elevation = if (pressed) 4.dp else 14.dp,
                shadowColor = color.copy(alpha = 0.55f),
            )
            .clip(shape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button) {
                haptics.confirm()
                onClick()
            }
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = TfType.label.copy(fontSize = TfType.title.fontSize), color = contentColor, maxLines = 1)
        }
    }
}

/** A filter-style pill: outlined glass, or dark filled when [selected] (like the reference). */
@Composable
fun GlassChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: ImageVector? = null,
    height: Dp = 44.dp,
) {
    val c = Tf.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animatedPressScale(pressed, 0.94f)
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(percent = 50)
    val fill = if (selected) c.chip else Color.Transparent
    Row(
        modifier = modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (selected) {
                    Modifier.liquidGlass(
                        null, shape,
                        GlassStyle(tint = fill, fallbackTint = fill, rimLight = Color.White.copy(alpha = 0.35f), rimDark = Color.Transparent, sheen = 0.12f),
                        elevation = 6.dp, shadowColor = c.shadow,
                    )
                } else {
                    Modifier.liquidGlass(LocalBackdrop.current, shape, GlassDefaults.regular(c), elevation = 0.dp)
                        .border(1.dp, c.hairline, shape)
                },
            )
            .clip(shape)
            .clickable(source, indication = null, role = Role.Button) {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = TfType.label.copy(fontSize = TfType.body.fontSize), color = if (selected) c.onChip else c.ink, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            Icon(trailing, null, tint = if (selected) c.onChip else c.ink, modifier = Modifier.size(20.dp))
        }
    }
}

/** A small rounded tag, like "New here" in the reference. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Tf.colors.accent, contentColor: Color = Tf.colors.onAccent) {
    Box(
        modifier
            .background(color, RoundedCornerShape(percent = 50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, style = TfType.caption.copy(fontSize = TfType.label.fontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = contentColor, maxLines = 1)
    }
}

/** A tappable value inside rows: "100 ms ›". */
@Composable
fun ValuePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val c = Tf.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animatedPressScale(pressed, 0.92f)
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 36.dp)
            .background(if (emphasized) c.accentSoft else c.hairline.copy(alpha = c.hairline.alpha * 0.6f), shape)
            .border(1.dp, if (c.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.7f), shape)
            .clip(shape)
            .clickable(source, indication = null, role = Role.Button) {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TfType.label, color = if (emphasized) c.accent else c.ink, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------
// Liquid indicator geometry (shared by the dock and segmented controls)
// ---------------------------------------------------------------------------------------

/**
 * Left and right edges of an indicator travelling between equally sized slots at
 * fractional [position]. The leading edge rushes ahead and the trailing edge lags, so
 * the indicator stretches mid-way like a droplet and snaps back as it settles.
 */
fun liquidSpan(position: Float, slotWidth: Float, inset: Float, count: Int): Pair<Float, Float> {
    val p = position.coerceIn(0f, (count - 1).toFloat())
    val i = floor(p).toInt().coerceAtMost(count - 1)
    val f = p - i
    val j = (i + 1).coerceAtMost(count - 1)
    val l0 = i * slotWidth + inset
    val r0 = (i + 1) * slotWidth - inset
    val l1 = j * slotWidth + inset
    val r1 = (j + 1) * slotWidth - inset
    val easeIn = f * f
    val easeOut = 1f - (1f - f) * (1f - f)
    return lerp(l0, l1, easeIn) to lerp(r0, r1, easeOut)
}

/** How stretched the indicator is right now: 0 when settled, 1 mid-way. */
fun liquidStretch(position: Float): Float {
    val f = position - floor(position)
    return 4f * f * (1f - f)
}

// ---------------------------------------------------------------------------------------
// Segmented control with a liquid indicator
// ---------------------------------------------------------------------------------------

@Composable
fun <T> GlassSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val c = Tf.colors
    val haptics = rememberHaptics()
    val index = options.indexOf(selected).coerceAtLeast(0)
    val position by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "segment",
    )
    var widthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(c.hairline.copy(alpha = c.hairline.alpha * 0.7f), shape)
            .onSizeChanged { widthPx = it.width.toFloat() },
    ) {
        if (widthPx > 0f) {
            val slot = widthPx / options.size
            val inset = with(density) { 4.dp.toPx() }
            val (l, r) = liquidSpan(position, slot, inset, options.size)
            val stretch = liquidStretch(position)
            Box(
                Modifier
                    .offset { IntOffset(l.roundToInt(), 0) }
                    .width(with(density) { (r - l).toDp() })
                    .height(46.dp)
                    .padding(vertical = 4.dp)
                    .graphicsLayer { scaleY = 1f - 0.12f * stretch }
                    .liquidGlass(
                        null, shape,
                        GlassStyle(
                            tint = if (c.isDark) Color.White.copy(alpha = 0.16f) else Color.White,
                            fallbackTint = if (c.isDark) Color.White.copy(alpha = 0.16f) else Color.White,
                            rimLight = Color.White.copy(alpha = if (c.isDark) 0.4f else 1f),
                            rimDark = Color.White.copy(alpha = 0.1f),
                            sheen = 0f,
                        ),
                        elevation = 4.dp, shadowColor = c.shadow,
                    ),
            )
        }
        Row(Modifier.fillMaxWidth().height(46.dp)) {
            options.forEachIndexed { i, o ->
                val active = 1f - kotlin.math.abs(position - i).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(shape)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                            if (o != selected) {
                                haptics.tick()
                                onSelect(o)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(o),
                        style = TfType.label,
                        color = androidx.compose.ui.graphics.lerp(c.inkSecondary, c.ink, active),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Switch: the thumb stretches as it travels, like a drop of liquid.
// ---------------------------------------------------------------------------------------

@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Tf.colors
    val haptics = rememberHaptics()
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "switch",
    )
    val trackW = 58.dp
    val trackH = 34.dp
    val thumbH = 28.dp
    val stretch = 4f * position.coerceIn(0f, 1f) * (1f - position.coerceIn(0f, 1f))
    Box(
        modifier
            .size(trackW, trackH)
            .drawBehind {
                val off = if (c.isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
                drawRoundRect(
                    color = androidx.compose.ui.graphics.lerp(off, c.success, position.coerceIn(0f, 1f)),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
            .clip(RoundedCornerShape(percent = 50))
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Switch) {
                haptics.tick()
                onCheckedChange(!checked)
            },
    ) {
        val pad = 3.dp
        val baseW = 28.dp
        val extra = 12.dp * stretch
        val travel = trackW - baseW - pad * 2
        Box(
            Modifier
                .offset(x = pad + travel * position - extra * position, y = (trackH - thumbH) / 2)
                .size(baseW + extra, thumbH)
                .liquidGlass(
                    null, RoundedCornerShape(percent = 50),
                    GlassStyle(tint = Color.White, fallbackTint = Color.White, rimLight = Color.White, rimDark = Color.White.copy(alpha = 0.6f), sheen = 0f),
                    elevation = 3.dp, shadowColor = Color.Black.copy(alpha = 0.3f),
                ),
        )
    }
}

// ---------------------------------------------------------------------------------------
// Slider: glass groove, accent fill, a lens-like thumb that grows while held.
// ---------------------------------------------------------------------------------------

/**
 * [value] and the callback use 0..1; map to real values (often logarithmically) outside.
 * [steps] > 0 snaps to that many intervals with a haptic tick at each.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: () -> Unit = {},
) {
    val c = Tf.colors
    val haptics = rememberHaptics()
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val currentValue by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "slider-lift",
    )
    val density = LocalDensity.current
    val thumbW = 38.dp
    val thumbH = 26.dp
    val trackH = 8.dp
    var lastStep by remember { mutableStateOf(-1) }

    fun snap(v: Float): Float {
        if (steps <= 0) return v.coerceIn(0f, 1f)
        val s = (v * steps).roundToInt().coerceIn(0, steps)
        if (s != lastStep) {
            if (lastStep != -1) haptics.tick()
            lastStep = s
        }
        return s / steps.toFloat()
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                val thumbPx = thumbW.toPx()
                fun toValue(x: Float) = ((x - thumbPx / 2) / (size.width - thumbPx)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    haptics.tick()
                    onChange(snap(toValue(down.position.x)))
                    val pointer = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointer } ?: break
                        if (!change.pressed) break
                        if (change.positionChanged()) {
                            change.consume()
                            val v = snap(toValue(change.position.x))
                            if (v != currentValue) onChange(v)
                        }
                    }
                    dragging = false
                    onFinished()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbPx = with(density) { thumbW.toPx() }
        val travel = (widthPx - thumbPx).coerceAtLeast(1f)
        val cx = thumbPx / 2 + travel * value.coerceIn(0f, 1f)
        // Groove and fill
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackH)
                .drawBehind {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(if (c.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f), cornerRadius = r)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.75f), c.accent)),
                        size = Size(cx, size.height),
                        cornerRadius = r,
                    )
                },
        )
        // Thumb
        val scale = 1f + 0.35f * lift
        Box(
            Modifier
                .offset { IntOffset((cx - thumbPx / 2).roundToInt(), 0) }
                .size(thumbW, thumbH)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .liquidGlass(
                    null,
                    RoundedCornerShape(percent = 50),
                    GlassStyle(
                        tint = Color.White.copy(alpha = 0.6f + 0.4f * (1f - lift)),
                        fallbackTint = Color.White.copy(alpha = 0.6f + 0.4f * (1f - lift)),
                        blur = 2.dp,
                        refraction = 1f,
                        bezel = 10.dp,
                        rimLight = Color.White,
                        rimDark = Color.White.copy(alpha = 0.5f),
                        sheen = 0f,
                    ),
                    elevation = (4 + 6 * lift).dp,
                    shadowColor = Color.Black.copy(alpha = 0.28f),
                ),
        )
    }
}

// ---------------------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------------------

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = TfType.label.copy(fontSize = TfType.body.fontSize), color = Tf.colors.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = TfType.caption.copy(fontSize = TfType.caption.fontSize), color = Tf.colors.inkSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Tf.colors.hairline))
}

/** Rounded square icon tile used at the start of rows. */
@Composable
fun IconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(
        modifier
            .size(size)
            .background(
                Brush.verticalGradient(listOf(color.copy(alpha = 0.9f), color)),
                RoundedCornerShape(size * 0.32f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

/** Little coloured dot with a number, used for targets everywhere. */
@Composable
fun TargetDot(index: Int, modifier: Modifier = Modifier, size: Dp = 28.dp, textStyle: TextStyle = TfType.caption) {
    val color = targetColor(index)
    Box(
        modifier
            .size(size)
            .background(color, CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("${index + 1}", style = textStyle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color.White)
    }
}

private val TARGET_COLORS = listOf(
    Color(0xFF7B5CFF), Color(0xFFFF6B8B), Color(0xFF16B9A0), Color(0xFFFF9F43),
    Color(0xFF3D8BFF), Color(0xFFE056FD), Color(0xFF2ECC71), Color(0xFFFFC312),
)

fun targetColor(index: Int): Color = TARGET_COLORS[((index % TARGET_COLORS.size) + TARGET_COLORS.size) % TARGET_COLORS.size]
