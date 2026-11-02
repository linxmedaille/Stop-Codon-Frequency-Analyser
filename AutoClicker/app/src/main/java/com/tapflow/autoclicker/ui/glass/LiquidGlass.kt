package com.tapflow.autoclicker.ui.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSimple
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tapflow.autoclicker.ui.theme.TapflowColors

/**
 * Something glass can see through. The source composable records itself into [layer]
 * every frame (see [backdropSource]); glass surfaces then redraw the part of it that sits
 * behind them, blurred and refracted.
 */
@Stable
class Backdrop internal constructor(val layer: GraphicsLayer) {
    /** Where the recorded content's origin sits, in root coordinates. */
    var originInRoot: Offset = Offset.Zero
        internal set
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Records this composable's drawing into [backdrop] and draws it as usual. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .then(BackdropOriginElement(backdrop))
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/** The aurora backdrop that in-page glass samples. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Whether real blur/refraction is available (Android 12+). */
val glassSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Whether the refraction shader is available (Android 13+). */
val lensSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@Stable
data class GlassStyle(
    val tint: Color,
    val blur: Dp = 22.dp,
    /** 0 = flat frosted glass, 1 = strong bending at the edges. */
    val refraction: Float = 0.55f,
    /** Width of the curved rim that bends light. */
    val bezel: Dp = 18.dp,
    val saturation: Float = 1.45f,
    /** Magnification of what's behind (used by the dock's lens). */
    val zoom: Float = 1f,
    val rimLight: Color = Color.White.copy(alpha = 0.85f),
    val rimDark: Color = Color.White.copy(alpha = 0.15f),
    val rimWidth: Dp = 1.dp,
    /** Soft white sheen across the top of the surface. */
    val sheen: Float = 0.22f,
    val fallbackTint: Color = tint,
)

object GlassDefaults {
    /**
     * Glass over the aurora. The aurora is already a soft gradient, so blurring it again
     * changes nothing visible but costs a full offscreen blur per surface; these surfaces
     * refract and tint instead. Surfaces over detailed content (dock, tray, sheets, photo
     * buttons) set an explicit blur.
     */
    fun regular(c: TapflowColors) = GlassStyle(
        tint = c.glass,
        blur = 0.dp,
        rimLight = c.rimLight,
        rimDark = c.rimDark,
        sheen = if (c.isDark) 0.08f else 0.24f,
        fallbackTint = c.glassFallback,
    )

    fun strong(c: TapflowColors) = regular(c).copy(tint = c.glassStrong, blur = 28.dp)

    /** Regular glass over busy content: blurred. */
    fun frosted(c: TapflowColors) = regular(c).copy(blur = 20.dp)

    fun clear(c: TapflowColors) = regular(c).copy(
        tint = if (c.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.22f),
        blur = 0.dp,
        refraction = 0.9f,
        sheen = if (c.isDark) 0.06f else 0.14f,
    )

    /** For glass floating over a photo/screenshot: darker, punchier. */
    fun overPhoto(c: TapflowColors) = GlassStyle(
        tint = if (c.isDark) Color(0x66121216) else Color(0x80FFFFFF),
        blur = 26.dp,
        refraction = 0.7f,
        rimLight = c.rimLight,
        rimDark = c.rimDark,
        sheen = if (c.isDark) 0.06f else 0.2f,
        fallbackTint = c.glassFallback,
    )
}

/**
 * Liquid glass: draws the [backdrop] behind this element blurred, colour-boosted and
 * (on Android 13+) refracted along a curved rim, then a tint, a top sheen and a specular
 * rim. Falls back to a near-opaque tint below Android 12.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop?,
    shape: Shape,
    style: GlassStyle,
    elevation: Dp = 0.dp,
    shadowColor: Color = Color.Black.copy(alpha = 0.18f),
): Modifier {
    val withShadow = if (elevation > 0.dp) {
        this.shadow(elevation, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
    } else {
        this
    }
    return withShadow.then(GlassElement(backdrop, shape, style))
}

private data class GlassElement(
    val backdrop: Backdrop?,
    val shape: Shape,
    val style: GlassStyle,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(backdrop, shape, style)
    override fun update(node: GlassNode) = node.update(backdrop, shape, style)
    override fun InspectorInfo.inspectableProperties() {
        name = "liquidGlass"
    }
}

private class GlassNode(
    var backdrop: Backdrop?,
    var shape: Shape,
    var style: GlassStyle,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var graphicsContext: GraphicsContext? = null
    private var layer: GraphicsLayer? = null
    private var positionInRoot = Offset.Zero
    private var effects: GlassEffects? = null

    override fun onAttach() {
        if (glassSupported) {
            val ctx = currentValueOf(LocalGraphicsContext)
            graphicsContext = ctx
            layer = ctx.createGraphicsLayer()
        }
    }

    override fun onDetach() {
        layer?.let { graphicsContext?.releaseGraphicsLayer(it) }
        layer = null
        graphicsContext = null
    }

    fun update(backdrop: Backdrop?, shape: Shape, style: GlassStyle) {
        this.backdrop = backdrop
        this.shape = shape
        // Keep the effect cache (and its compiled shader): it rebuilds itself only when
        // the parameters that shape the effect change, not when the tint animates.
        this.style = style
        invalidateDraw()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val p = coordinates.positionInRoot()
        if (p != positionInRoot) {
            positionInRoot = p
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val outline = shape.createOutline(size, layoutDirection, this)
        val source = backdrop
        val l = layer
        if (source != null && l != null && glassSupported) {
            val fx = effects ?: GlassEffects().also { effects = it }
            l.renderEffect = fx.effectFor(this, size, outline, style)
            applyOutline(l, outline, size)
            l.clip = true
            val dx = positionInRoot.x - source.originInRoot.x
            val dy = positionInRoot.y - source.originInRoot.y
            l.record {
                translate(-dx, -dy) { drawLayer(source.layer) }
            }
            drawLayer(l)
            drawOutline(outline, style.tint)
        } else {
            drawOutline(outline, style.fallbackTint)
        }
        drawGlassFinish(outline, style)
        drawContent()
    }
}

internal fun applyOutline(layer: GraphicsLayer, outline: Outline, size: Size) {
    when (outline) {
        is Outline.Rectangle -> layer.setRectOutline(Offset.Zero, size)
        is Outline.Rounded -> {
            val rr = outline.roundRect
            if (rr.isSimple) {
                layer.setRoundRectOutline(Offset.Zero, size, rr.topLeftCornerRadius.x)
            } else {
                layer.setPathOutline(Path().apply { addRoundRect(rr) })
            }
        }
        is Outline.Generic -> layer.setPathOutline(outline.path)
    }
}

/** Sheen + specular rim, shared by every glass surface. */
internal fun DrawScope.drawGlassFinish(outline: Outline, style: GlassStyle) {
    if (style.sheen > 0f) {
        drawOutline(
            outline,
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = style.sheen),
                0.45f to Color.White.copy(alpha = 0f),
                startY = 0f,
                endY = size.height,
            ),
        )
    }
    val w = style.rimWidth.toPx()
    drawOutline(
        outline,
        Brush.linearGradient(
            0f to style.rimLight,
            0.35f to style.rimDark,
            0.65f to style.rimDark.copy(alpha = style.rimDark.alpha * 0.5f),
            1f to style.rimLight.copy(alpha = style.rimLight.alpha * 0.55f),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        style = Stroke(width = w),
    )
}

/** Caches the render effect for a glass surface; rebuilt only when size or style change. */
internal class GlassEffects {
    private var key: Any? = null
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null
    private var shader: Any? = null

    fun effectFor(
        density: androidx.compose.ui.unit.Density,
        size: Size,
        outline: Outline,
        style: GlassStyle,
    ): androidx.compose.ui.graphics.RenderEffect? {
        if (!glassSupported) return null
        val radius = cornerRadiusOf(outline, size)
        val k = listOf(size, radius, style.blur, style.refraction, style.bezel, style.saturation, style.zoom)
        if (k == key) return effect
        key = k
        effect = with(density) {
            buildEffect(size, radius, style.blur.toPx(), style.bezel.toPx(), style.refraction, style.saturation, style.zoom)
        }
        return effect
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun buildEffect(
        size: Size,
        radius: Float,
        blurPx: Float,
        bezelPx: Float,
        refraction: Float,
        saturation: Float,
        zoom: Float,
    ): androidx.compose.ui.graphics.RenderEffect {
        var fx: android.graphics.RenderEffect? = null
        if (blurPx > 0.5f) {
            fx = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        }
        if (saturation != 1f) {
            val cm = ColorMatrix().apply { setSaturation(saturation) }
            val sat = android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm))
            fx = if (fx == null) sat else android.graphics.RenderEffect.createChainEffect(sat, fx)
        }
        if (lensSupported && (refraction > 0f || zoom != 1f) && size.minDimension > 2f) {
            val lens = lensEffect(size, radius, bezelPx, refraction, zoom)
            fx = if (fx == null) lens else android.graphics.RenderEffect.createChainEffect(lens, fx)
        }
        if (fx == null) {
            fx = android.graphics.RenderEffect.createOffsetEffect(0f, 0f)
        }
        return fx!!.asComposeRenderEffect()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun lensEffect(size: Size, radius: Float, bezel: Float, strength: Float, zoom: Float): android.graphics.RenderEffect {
        val s = (shader as? RuntimeShader) ?: RuntimeShader(LENS_SHADER).also { shader = it }
        s.setFloatUniform("size", size.width, size.height)
        s.setFloatUniform("radius", radius)
        s.setFloatUniform("bezel", bezel.coerceAtMost(size.minDimension / 2f).coerceAtLeast(1f))
        s.setFloatUniform("strength", strength)
        s.setFloatUniform("zoom", zoom.coerceAtLeast(0.1f))
        return android.graphics.RenderEffect.createRuntimeShaderEffect(s, "content")
    }
}

internal fun cornerRadiusOf(outline: Outline, size: Size): Float = when (outline) {
    is Outline.Rectangle -> 0f
    is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
    is Outline.Generic -> size.minDimension / 2f
}

/**
 * AGSL lens: inside a rounded rectangle, pixels within [bezel] of the edge sample from
 * further inside, following a circular profile, which bends the backdrop like the rim of
 * a glass droplet. Red and blue are offset slightly for a hint of dispersion. [zoom]
 * magnifies the whole surface around its centre.
 */
internal const val LENS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bezel;
uniform float strength;
uniform float zoom;

float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float r = min(radius, min(halfSize.x, halfSize.y));
    float d = sdRoundBox(p, halfSize, r);
    float2 base = halfSize + p / zoom;
    float inner = -d;
    if (inner >= bezel || inner < 0.0) {
        return content.eval(base);
    }
    float e = 0.75;
    float dx = sdRoundBox(p + float2(e, 0.0), halfSize, r) - sdRoundBox(p - float2(e, 0.0), halfSize, r);
    float dy = sdRoundBox(p + float2(0.0, e), halfSize, r) - sdRoundBox(p - float2(0.0, e), halfSize, r);
    float2 g = float2(dx, dy);
    float len = length(g);
    float2 n = len > 0.0001 ? g / len : float2(0.0, 0.0);
    float t = 1.0 - inner / bezel;
    float bend = strength * bezel * (1.0 - sqrt(max(0.0, 1.0 - t * t)));
    float2 off = -n * bend;
    half4 c = content.eval(base + off);
    half rr = content.eval(base + off * 1.12).r;
    half bb = content.eval(base + off * 0.88).b;
    return half4(rr, c.g, bb, c.a);
}
"""

private data class BackdropOriginElement(val backdrop: Backdrop) : ModifierNodeElement<BackdropOriginNode>() {
    override fun create() = BackdropOriginNode(backdrop)
    override fun update(node: BackdropOriginNode) {
        node.backdrop = backdrop
    }
}

private class BackdropOriginNode(var backdrop: Backdrop) : Modifier.Node(), GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        backdrop.originInRoot = coordinates.positionInRoot()
    }
}

/** A rectangle glass slab helper for full-bleed panels. */
fun Modifier.liquidGlassRect(backdrop: Backdrop?, style: GlassStyle): Modifier =
    liquidGlass(backdrop, RectangleShape, style)
