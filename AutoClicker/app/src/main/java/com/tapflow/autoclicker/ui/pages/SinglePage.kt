package com.tapflow.autoclicker.ui.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.autoclicker.data.Store
import com.tapflow.autoclicker.service.RunStatus
import com.tapflow.autoclicker.ui.AppUi
import com.tapflow.autoclicker.ui.SheetRequest
import com.tapflow.autoclicker.ui.board.BoardPreviewCard
import com.tapflow.autoclicker.ui.components.PageGutter
import com.tapflow.autoclicker.ui.components.PageHeader
import com.tapflow.autoclicker.ui.components.RunBanner
import com.tapflow.autoclicker.ui.components.TopChips
import com.tapflow.autoclicker.ui.glass.GlassCard
import com.tapflow.autoclicker.ui.glass.GlassChip
import com.tapflow.autoclicker.ui.glass.GlassSlider
import com.tapflow.autoclicker.ui.glass.Hairline
import com.tapflow.autoclicker.ui.glass.IconTile
import com.tapflow.autoclicker.ui.glass.Kicker
import com.tapflow.autoclicker.ui.glass.PrimaryButton
import com.tapflow.autoclicker.ui.glass.SettingRow
import com.tapflow.autoclicker.ui.glass.ValuePill
import com.tapflow.autoclicker.ui.theme.Tf
import com.tapflow.autoclicker.ui.theme.TfType
import com.tapflow.core.Estimate
import com.tapflow.core.Mode
import com.tapflow.core.NPoint
import com.tapflow.core.SingleConfig
import com.tapflow.core.formatDuration
import kotlin.math.roundToInt

private const val MIN_INTERVAL = 10L
private const val MAX_INTERVAL = 60_000L

private val RATE_PRESETS = listOf(
    "20 / s" to 50L,
    "10 / s" to 100L,
    "5 / s" to 200L,
    "2 / s" to 500L,
    "1 / s" to 1_000L,
)

private fun Store.updateSingle(transform: (SingleConfig) -> SingleConfig) = update { it.copy(single = transform(it.single)) }

private fun rateLabel(intervalMs: Long): String {
    val perSec = Estimate.tapsPerSecond(intervalMs)
    return when {
        perSec >= 10 -> "${perSec.roundToInt()} taps a second"
        perSec >= 1 -> "%.1f taps a second".format(perSec).replace(".0 ", " ")
        else -> "one tap every ${formatDuration(intervalMs)}"
    }
}

@Composable
fun SinglePage(
    store: Store,
    ui: AppUi,
    serviceOn: Boolean,
    status: RunStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTune: () -> Unit,
) {
    val ws by store.workspace.collectAsStateWithLifecycle()
    val cfg = ws.single
    val c = Tf.colors

    PageScroll {
        TopChips(serviceOn, onService = { ui.show(SheetRequest.Permission) }, onTune = onTune) {
            RATE_PRESETS.forEach { (label, ms) ->
                GlassChip(label, selected = cfg.intervalMs == ms, onClick = { store.updateSingle { it.copy(intervalMs = ms) } })
            }
        }
        RunBanner(status, onStop)
        PageHeader(
            title = "Single tap",
            subtitle = "One point, on a steady rhythm",
            badge = if (cfg.board.targets.isEmpty()) "Set a point" else cfg.board.targets.first().name,
        )
        BoardPreviewCard(
            board = cfg.board,
            onEdit = {
                // Drop a starting point in the middle so the editor has something to drag.
                if (cfg.board.targets.isEmpty()) {
                    store.updateBoard(Mode.Single) { it.addTarget(NPoint(0.5f, 0.5f)).first }
                }
                ui.editor = Mode.Single
            },
            onPickScreenshot = { ui.pickScreenshot(Mode.Single) },
            modifier = Modifier.padding(horizontal = PageGutter),
        )
        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.padding(horizontal = PageGutter)) {
            Kicker("Rhythm")
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                    ui.show(
                        SheetRequest.Duration(
                            title = "Time between taps",
                            valueMs = cfg.intervalMs,
                            minMs = MIN_INTERVAL,
                            maxMs = 24L * 3_600_000,
                            presets = listOf(10, 25, 50, 100, 250, 500, 1_000, 5_000, 20_000, 60_000),
                            onSet = { v -> store.updateSingle { it.copy(intervalMs = v) } },
                        ),
                    )
                },
            ) {
                AnimatedContent(
                    targetState = formatDuration(cfg.intervalMs),
                    transitionSpec = {
                        (slideInVertically(spring(0.8f, 600f)) { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut())
                    },
                    label = "interval",
                ) { text ->
                    Text(text, style = TfType.number, color = c.ink)
                }
                Spacer(Modifier.width(10.dp))
                Text("between taps", style = TfType.body, color = c.inkSecondary, modifier = Modifier.padding(bottom = 9.dp))
            }
            Text(rateLabel(cfg.intervalMs), style = TfType.label, color = c.accent)
            Spacer(Modifier.height(10.dp))
            GlassSlider(
                value = msToSlider(cfg.intervalMs, MIN_INTERVAL, MAX_INTERVAL),
                onValueChange = { v -> store.updateSingle { it.copy(intervalMs = sliderToMs(v, MIN_INTERVAL, MAX_INTERVAL)) } },
            )
            Row {
                Text("faster", style = TfType.caption, color = c.inkTertiary, modifier = Modifier.weight(1f))
                Text("slower", style = TfType.caption, color = c.inkTertiary)
            }
            Hairline(Modifier.padding(vertical = 12.dp))
            SettingRow(
                title = "Hold each tap",
                subtitle = "Longer holds help some games register taps",
                leading = { IconTile(Icons.Rounded.Timer, Color(0xFF7B5CFF)) },
            ) {
                ValuePill(formatDuration(cfg.holdMs), onClick = {
                    ui.show(
                        SheetRequest.Duration(
                            title = "Hold each tap for",
                            valueMs = cfg.holdMs,
                            minMs = 1,
                            maxMs = 60_000,
                            presets = listOf(1, 10, 20, 50, 100, 250, 500, 1_000),
                            onSet = { v -> store.updateSingle { it.copy(holdMs = v) } },
                        ),
                    )
                })
            }
        }
        Spacer(Modifier.height(14.dp))
        RepeatCard(
            ui = ui,
            policy = cfg.repeat,
            onChange = { p -> store.updateSingle { it.copy(repeat = p) } },
            cycleMs = cfg.intervalMs,
            noun = "taps",
            modifier = Modifier.padding(horizontal = PageGutter),
        )
        Spacer(Modifier.height(14.dp))
        HumanizeCard(
            humanize = cfg.humanize,
            onChange = { h -> store.updateSingle { it.copy(humanize = h) } },
            modifier = Modifier.padding(horizontal = PageGutter),
        )
        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            text = "Start clicking",
            onClick = onStart,
            icon = Icons.Rounded.PlayArrow,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter),
        )
    }
}
