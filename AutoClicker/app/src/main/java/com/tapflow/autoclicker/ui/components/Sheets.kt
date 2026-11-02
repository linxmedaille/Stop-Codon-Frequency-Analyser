package com.tapflow.autoclicker.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tapflow.autoclicker.ui.AppUi
import com.tapflow.autoclicker.ui.SheetRequest
import com.tapflow.autoclicker.ui.Toast
import com.tapflow.autoclicker.ui.glass.Backdrop
import com.tapflow.autoclicker.ui.glass.GlassChip
import com.tapflow.autoclicker.ui.glass.GlassDefaults
import com.tapflow.autoclicker.ui.glass.GlassIconButton
import com.tapflow.autoclicker.ui.glass.GlassSegmented
import com.tapflow.autoclicker.ui.glass.GlassSurface
import com.tapflow.autoclicker.ui.glass.GlassSwitch
import com.tapflow.autoclicker.ui.glass.Kicker
import com.tapflow.autoclicker.ui.glass.PrimaryButton
import com.tapflow.autoclicker.ui.glass.TargetDot
import com.tapflow.autoclicker.ui.glass.rememberHaptics
import com.tapflow.autoclicker.ui.theme.Tf
import com.tapflow.autoclicker.ui.theme.TfType
import com.tapflow.core.formatCount
import com.tapflow.core.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Scrim + a glass sheet that springs up from the bottom and can be flicked away. */
@Composable
fun SheetHost(ui: AppUi, backdrop: Backdrop?) {
    val request = ui.sheet
    var last by remember { mutableStateOf<SheetRequest?>(null) }
    LaunchedEffect(request) { if (request != null) last = request }
    val shown = request ?: last
    val c = Tf.colors
    BackHandler(enabled = request != null) { ui.dismissSheet() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = request != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.scrim)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { ui.dismissSheet() },
            )
        }
        AnimatedVisibility(
            visible = request != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.82f, stiffness = 380f)) { it } +
                scaleIn(spring(dampingRatio = 0.7f, stiffness = 380f), initialScale = 0.92f),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(200)),
        ) {
            val dragY = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(request) { dragY.snapTo(0f) }
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .graphicsLayer { translationY = dragY.value }
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
                shape = RoundedCornerShape(38.dp),
                style = GlassDefaults.strong(c).copy(blur = 24.dp, refraction = 0.8f, bezel = 26.dp),
                backdrop = backdrop,
                elevation = 30.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp)
                        .padding(bottom = 22.dp),
                ) {
                    // Grabber: drag down to dismiss.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        scope.launch {
                                            if (dragY.value > 120f) ui.dismissSheet() else dragY.animateTo(0f, spring(0.7f, 500f))
                                        }
                                    },
                                ) { change, dy ->
                                    change.consume()
                                    scope.launch { dragY.snapTo((dragY.value + dy).coerceAtLeast(0f)) }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(40.dp, 5.dp).background(c.inkTertiary.copy(alpha = 0.5f), CircleShape))
                    }
                    when (val r = shown) {
                        is SheetRequest.Duration -> DurationSheet(r) { ui.dismissSheet() }
                        is SheetRequest.Count -> CountSheet(r) { ui.dismissSheet() }
                        is SheetRequest.PickTarget -> PickTargetSheet(r) { ui.dismissSheet() }
                        SheetRequest.Permission -> PermissionSheet { ui.dismissSheet() }
                        null -> Unit
                    }
                }
            }
        }
    }
}

private enum class TimeUnit(val label: String, val ms: Long) { Ms("ms", 1), Sec("sec", 1_000), Min("min", 60_000), Hour("hr", 3_600_000) }

private fun bestUnit(ms: Long): TimeUnit = when {
    ms < 1_000 -> TimeUnit.Ms
    ms < 120_000 -> TimeUnit.Sec
    ms < 7_200_000 -> TimeUnit.Min
    else -> TimeUnit.Hour
}

private fun formatIn(ms: Long, unit: TimeUnit): String {
    if (unit == TimeUnit.Ms) return ms.toString()
    val v = ms.toDouble() / unit.ms
    val rounded = (v * 100).roundToLong() / 100.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

@Composable
private fun SheetTitle(title: String, note: String?) {
    Text(title, style = TfType.editorial, color = Tf.colors.ink)
    if (note != null) {
        Spacer(Modifier.height(6.dp))
        Text(note, style = TfType.body, color = Tf.colors.inkSecondary)
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun BigNumberField(
    text: String,
    onText: (String) -> Unit,
    suffix: String,
    onDone: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    decimal: Boolean,
) {
    val c = Tf.colors
    val focus = remember { FocusRequester() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(Icons.Rounded.Remove, "Less", onMinus, size = 52.dp)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (c.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.55f))
                .border(1.dp, c.hairline, RoundedCornerShape(24.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) { focus.requestFocus() },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = text,
                    onValueChange = { v -> onText(v.filter { it.isDigit() || (decimal && it == '.') }.take(9)) },
                    textStyle = TfType.number.copy(color = c.ink, textAlign = TextAlign.End),
                    singleLine = true,
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    modifier = Modifier.width(IntrinsicWidthFor(text)).focusRequester(focus),
                )
                Spacer(Modifier.width(8.dp))
                Text(suffix, style = TfType.title, color = c.inkSecondary, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
        GlassIconButton(Icons.Rounded.Add, "More", onPlus, size = 52.dp)
    }
}

/** Width that comfortably fits [text] in the big serif style. */
private fun IntrinsicWidthFor(text: String) = (28 * maxOf(1, text.length) + 12).dp

@Composable
private fun DurationSheet(r: SheetRequest.Duration, close: () -> Unit) {
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current
    var unit by remember(r) { mutableStateOf(bestUnit(r.valueMs)) }
    var text by remember(r) { mutableStateOf(formatIn(r.valueMs, bestUnit(r.valueMs))) }
    fun currentMs(): Long = ((text.toDoubleOrNull() ?: 0.0) * unit.ms).roundToLong().coerceIn(r.minMs, r.maxMs)
    fun set(ms: Long) {
        val clamped = ms.coerceIn(r.minMs, r.maxMs)
        text = formatIn(clamped, unit)
    }
    fun commit() {
        focusManager.clearFocus()
        r.onSet(currentMs())
        close()
    }
    val step = when (unit) {
        TimeUnit.Ms -> if (currentMs() < 100) 5L else 10L
        TimeUnit.Sec -> if (currentMs() < 10_000) 500L else 1_000L
        TimeUnit.Min -> 60_000L
        TimeUnit.Hour -> 3_600_000L
    }
    SheetTitle(r.title, r.note)
    BigNumberField(
        text = text,
        onText = { text = it },
        suffix = unit.label,
        onDone = ::commit,
        onMinus = { haptics.tick(); set(currentMs() - step) },
        onPlus = { haptics.tick(); set(currentMs() + step) },
        decimal = unit != TimeUnit.Ms,
    )
    Spacer(Modifier.height(16.dp))
    GlassSegmented(
        options = TimeUnit.entries.toList(),
        selected = unit,
        onSelect = { u ->
            val ms = currentMs()
            unit = u
            text = formatIn(ms, u)
        },
        label = { it.label },
    )
    Spacer(Modifier.height(16.dp))
    Kicker("Presets")
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        r.presets.filter { it in r.minMs..r.maxMs }.forEach { p ->
            GlassChip(
                text = formatDuration(p),
                selected = p == currentMs(),
                onClick = {
                    unit = bestUnit(p)
                    text = formatIn(p, unit)
                },
            )
        }
    }
    Spacer(Modifier.height(22.dp))
    PrimaryButton("Set ${formatDuration(currentMs())}", ::commit, Modifier.fillMaxWidth(), icon = Icons.Rounded.Check)
}

@Composable
private fun CountSheet(r: SheetRequest.Count, close: () -> Unit) {
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current
    var forever by remember(r) { mutableStateOf(r.allowForever && r.value <= 0) }
    var text by remember(r) { mutableStateOf(if (r.value <= 0) "10" else r.value.toString()) }
    fun current(): Int = (text.toIntOrNull() ?: r.min).coerceIn(r.min, r.max)
    fun commit() {
        focusManager.clearFocus()
        r.onSet(if (forever) 0 else current())
        close()
    }
    SheetTitle(r.title, null)
    Box(Modifier.graphicsLayer { alpha = if (forever) 0.35f else 1f }) {
        BigNumberField(
            text = text,
            onText = { text = it; forever = false },
            suffix = r.suffix,
            onDone = ::commit,
            onMinus = { haptics.tick(); forever = false; text = (current() - 1).coerceAtLeast(r.min).toString() },
            onPlus = { haptics.tick(); forever = false; text = (current() + 1).coerceAtMost(r.max).toString() },
            decimal = false,
        )
    }
    if (r.allowForever) {
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Tf.colors.hairline.copy(alpha = 0.06f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AllInclusive, null, tint = Tf.colors.accent)
            Spacer(Modifier.width(12.dp))
            Text("Forever (until you stop it)", style = TfType.label, color = Tf.colors.ink, modifier = Modifier.weight(1f))
            GlassSwitch(forever, { forever = it })
        }
    }
    Spacer(Modifier.height(16.dp))
    Kicker("Presets")
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        r.presets.filter { it in r.min..r.max }.forEach { p ->
            GlassChip(
                text = "${formatCount(p.toLong())}×",
                selected = !forever && p == current(),
                onClick = { forever = false; text = p.toString() },
            )
        }
    }
    Spacer(Modifier.height(22.dp))
    PrimaryButton(
        if (forever) "Repeat forever" else "Set ${formatCount(current().toLong())} ${r.suffix}",
        ::commit,
        Modifier.fillMaxWidth(),
        icon = Icons.Rounded.Check,
    )
}

@Composable
private fun PickTargetSheet(r: SheetRequest.PickTarget, close: () -> Unit) {
    val c = Tf.colors
    val haptics = rememberHaptics()
    SheetTitle(r.title, if (r.board.targets.isEmpty()) "No points yet. Place some on your screenshot." else null)
    r.board.targets.forEachIndexed { i, t ->
        val selected = t.id == r.selected
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (selected) c.accentSoft else Color.Transparent)
                .clickable {
                    haptics.tick()
                    r.onPick(t.id)
                    close()
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TargetDot(i, size = 34.dp, textStyle = TfType.label)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = TfType.title, color = c.ink)
                Text(
                    "${(t.point.x * 100).roundToInt()}% across · ${(t.point.y * 100).roundToInt()}% down",
                    style = TfType.caption,
                    color = c.inkSecondary,
                )
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = c.accent)
        }
    }
    Spacer(Modifier.height(18.dp))
    PrimaryButton(
        "Edit points on screenshot",
        { close(); r.onEditPoints() },
        Modifier.fillMaxWidth(),
        icon = Icons.Rounded.CenterFocusStrong,
    )
}

@Composable
private fun PermissionSheet(close: () -> Unit) {
    val c = Tf.colors
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(88.dp)
                .background(Brush.linearGradient(listOf(Color(0xFFB993FF), c.accent)), CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.TouchApp, null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
    }
    Spacer(Modifier.height(18.dp))
    Text("One switch to flip", style = TfType.editorial, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Text(
        "Android only lets accessibility services tap other apps. Tapflow's service performs your taps and nothing else: it doesn't read your screen or send data anywhere.",
        style = TfType.body,
        color = c.inkSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Step(1, "Tap Open settings below")
    Step(2, "Find Tapflow auto clicker (under Downloaded or Installed apps)")
    Step(3, "Switch it on and confirm")
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.warning.copy(alpha = 0.12f))
            .padding(14.dp),
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = c.warning, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Switch greyed out? On Android 13+ apps installed outside the Play Store need: App info → ⋮ → Allow restricted settings.",
                style = TfType.caption.copy(fontSize = TfType.label.fontSize * 0.92f),
                color = c.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Open App info",
                style = TfType.label.copy(fontWeight = FontWeight.SemiBold),
                color = c.accent,
                modifier = Modifier.clickable { AppUi.openAppInfo(context) },
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    PrimaryButton(
        "Open settings",
        {
            AppUi.openAccessibilitySettings(context)
            close()
        },
        Modifier.fillMaxWidth(),
        icon = Icons.Rounded.Settings,
    )
}

@Composable
private fun Step(n: Int, text: String) {
    val c = Tf.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(c.chip, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", style = TfType.caption.copy(fontWeight = FontWeight.Bold), color = c.onChip)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = TfType.label, color = c.ink)
    }
}

/** A glass pill that drops in from the top for a few seconds. */
@Composable
fun ToastHost(ui: AppUi, backdrop: Backdrop?, modifier: Modifier = Modifier) {
    val toast = ui.toast
    var last by remember { mutableStateOf<Toast?>(null) }
    LaunchedEffect(toast) { if (toast != null) last = toast }
    val shown = toast ?: last
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(3_200)
            if (ui.toast?.id == toast.id) ui.toast = null
        }
    }
    val c = Tf.colors
    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier,
        enter = slideInVertically(spring(0.7f, 420f)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically(tween(260)) { -it * 2 } + fadeOut(),
    ) {
        val t = shown ?: return@AnimatedVisibility
        GlassSurface(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null) { ui.toast = null },
            shape = RoundedCornerShape(percent = 50),
            style = GlassDefaults.strong(c),
            backdrop = backdrop,
            elevation = 18.dp,
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (t.isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                    null,
                    tint = if (t.isError) c.danger else c.success,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(t.message, style = TfType.label, color = c.ink)
            }
        }
    }
}
