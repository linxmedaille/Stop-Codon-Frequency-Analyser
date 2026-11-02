package com.tapflow.autoclicker.ui.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tapflow.autoclicker.ui.AppUi
import com.tapflow.autoclicker.ui.SheetRequest
import com.tapflow.autoclicker.ui.components.DockClearance
import com.tapflow.autoclicker.ui.glass.GlassCard
import com.tapflow.autoclicker.ui.glass.GlassSegmented
import com.tapflow.autoclicker.ui.glass.GlassSlider
import com.tapflow.autoclicker.ui.glass.GlassSwitch
import com.tapflow.autoclicker.ui.glass.Hairline
import com.tapflow.autoclicker.ui.glass.IconTile
import com.tapflow.autoclicker.ui.glass.Kicker
import com.tapflow.autoclicker.ui.glass.SettingRow
import com.tapflow.autoclicker.ui.glass.ValuePill
import com.tapflow.autoclicker.ui.theme.Tf
import com.tapflow.autoclicker.ui.theme.TfType
import com.tapflow.core.Estimate
import com.tapflow.core.Humanize
import com.tapflow.core.RepeatMode
import com.tapflow.core.RepeatPolicy
import com.tapflow.core.formatCount
import com.tapflow.core.formatDuration
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A scrolling page body that starts under the status bar and clears the dock. */
@Composable
fun PageScroll(content: @Composable ColumnScope.() -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(top = top + 6.dp, bottom = DockClearance)),
        content = content,
    )
}

// ---- Log-scale slider mapping: equal slider travel per factor of ten feels natural. ----

fun msToSlider(ms: Long, min: Long, max: Long): Float =
    (ln(ms.coerceIn(min, max).toDouble() / min) / ln(max.toDouble() / min)).toFloat().coerceIn(0f, 1f)

fun sliderToMs(v: Float, min: Long, max: Long): Long {
    val raw = min * (max.toDouble() / min).pow(v.toDouble())
    val step = when {
        raw < 100 -> 1.0
        raw < 1_000 -> 10.0
        raw < 10_000 -> 100.0
        raw < 60_000 -> 1_000.0
        else -> 5_000.0
    }
    return ((raw / step).roundToLong() * step).toLong().coerceIn(min, max)
}

/** Forever / N times / for a duration, with a live total estimate. */
@Composable
fun RepeatCard(
    ui: AppUi,
    policy: RepeatPolicy,
    onChange: (RepeatPolicy) -> Unit,
    cycleMs: Long,
    noun: String,
    modifier: Modifier = Modifier,
) {
    val c = Tf.colors
    GlassCard(modifier) {
        Kicker("Keep going")
        Spacer(Modifier.height(12.dp))
        GlassSegmented(
            options = RepeatMode.entries.toList(),
            selected = policy.mode,
            onSelect = { onChange(policy.copy(mode = it)) },
            label = {
                when (it) {
                    RepeatMode.Forever -> "Until stopped"
                    RepeatMode.Times -> "Count"
                    RepeatMode.Duration -> "Timer"
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        AnimatedContent(
            targetState = policy.mode,
            transitionSpec = { (fadeIn(spring(stiffness = 500f)) togetherWith fadeOut(spring(stiffness = 900f))) },
            label = "repeat-mode",
        ) { mode ->
            when (mode) {
                RepeatMode.Forever -> SettingRow(
                    title = "Runs until you stop it",
                    subtitle = "Use ■ on the floating panel, or volume-down",
                )
                RepeatMode.Times -> SettingRow(title = "Number of $noun") {
                    ValuePill("${formatCount(policy.times.toLong())}×", emphasized = true, onClick = {
                        ui.show(
                            SheetRequest.Count(
                                title = "How many $noun?",
                                value = policy.times,
                                presets = listOf(10, 50, 100, 500, 1_000, 5_000, 10_000),
                                onSet = { onChange(policy.copy(times = it)) },
                            ),
                        )
                    })
                }
                RepeatMode.Duration -> SettingRow(title = "Stop after") {
                    ValuePill(formatDuration(policy.durationMs), emphasized = true, onClick = {
                        ui.show(
                            SheetRequest.Duration(
                                title = "Run for how long?",
                                valueMs = policy.durationMs,
                                minMs = 1_000,
                                presets = listOf(10_000, 30_000, 60_000, 300_000, 600_000, 1_800_000, 3_600_000),
                                onSet = { onChange(policy.copy(durationMs = it)) },
                            ),
                        )
                    })
                }
            }
        }
        val total = Estimate.totalMs(cycleMs, policy)
        Hairline(Modifier.padding(vertical = 4.dp))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Total", style = TfType.label, color = c.inkSecondary, modifier = Modifier.weight(1f))
            Text(
                when {
                    total == null -> "until stopped"
                    policy.mode == RepeatMode.Duration -> "${formatDuration(total)} · ≈ ${formatCount(total / cycleMs.coerceAtLeast(1))} $noun"
                    else -> "≈ ${formatDuration(total)}"
                },
                style = TfType.label,
                color = c.ink,
            )
        }
    }
}

/** Optional randomness so taps look less mechanical. */
@Composable
fun HumanizeCard(humanize: Humanize, onChange: (Humanize) -> Unit, modifier: Modifier = Modifier) {
    val c = Tf.colors
    GlassCard(modifier) {
        SettingRow(
            title = "Humanize",
            subtitle = "Vary each tap's position and timing a little",
            leading = { IconTile(Icons.Rounded.Fingerprint, Color(0xFF16B9A0)) },
        ) {
            GlassSwitch(humanize.enabled, { onChange(humanize.copy(enabled = it)) })
        }
        AnimatedVisibility(
            visible = humanize.enabled,
            enter = expandVertically(spring(0.85f, 500f)) + fadeIn(),
            exit = shrinkVertically(spring(0.9f, 700f)) + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Position spread", style = TfType.label, color = c.ink, modifier = Modifier.weight(1f))
                    Text("±${humanize.offsetPx} px", style = TfType.label, color = c.inkSecondary)
                }
                GlassSlider(
                    value = humanize.offsetPx / 40f,
                    onValueChange = { onChange(humanize.copy(offsetPx = (it * 40).roundToInt())) },
                    steps = 40,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Timing spread", style = TfType.label, color = c.ink, modifier = Modifier.weight(1f))
                    Text("±${humanize.timingPct}%", style = TfType.label, color = c.inkSecondary)
                }
                GlassSlider(
                    value = humanize.timingPct / 50f,
                    onValueChange = { onChange(humanize.copy(timingPct = (it * 50).roundToInt())) },
                    steps = 50,
                )
            }
        }
    }
}
