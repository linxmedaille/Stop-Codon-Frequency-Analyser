package com.tapflow.autoclicker.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tapflow.autoclicker.service.Phase
import com.tapflow.autoclicker.service.RunStatus
import com.tapflow.autoclicker.ui.glass.Badge
import com.tapflow.autoclicker.ui.glass.GlassChip
import com.tapflow.autoclicker.ui.glass.GlassIconButton
import com.tapflow.autoclicker.ui.theme.Tf
import com.tapflow.autoclicker.ui.theme.TfType

/** Horizontal page gutter. */
val PageGutter = 20.dp

/** Space at the bottom of every page so content clears the floating dock. */
val DockClearance = 128.dp

/**
 * The row of filter-style pills at the top of each page: a round settings button, the
 * service status pill, then page-specific quick choices.
 */
@Composable
fun TopChips(
    serviceOn: Boolean,
    onService: () -> Unit,
    onTune: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PageGutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Icons.Rounded.Tune, "Settings", onTune, size = 44.dp, elevation = 0.dp)
        ServiceChip(serviceOn, onService)
        content()
    }
}

@Composable
fun ServiceChip(on: Boolean, onClick: () -> Unit) {
    val c = Tf.colors
    GlassChip(
        text = if (on) "Service on" else "Turn on service",
        selected = on,
        onClick = onClick,
        leading = { PulsingDot(if (on) c.success else c.warning, pulse = false) },
    )
}

@Composable
fun PulsingDot(color: Color, pulse: Boolean, modifier: Modifier = Modifier) {
    // Only animate while it means something (a routine running); idle screens stay still.
    if (!pulse) {
        Box(modifier.size(10.dp).drawBehind { drawCircle(color) })
        return
    }
    val t = rememberInfiniteTransition(label = "dot")
    val s by t.animateFloat(1f, 2.2f, infiniteRepeatable(tween(1_100), RepeatMode.Restart), label = "dot-s")
    val a by t.animateFloat(0.5f, 0f, infiniteRepeatable(tween(1_100), RepeatMode.Restart), label = "dot-a")
    Box(
        modifier
            .size(10.dp)
            .drawBehind {
                drawCircle(color.copy(alpha = a), radius = size.minDimension / 2f * s)
                drawCircle(color)
            },
    )
}

/** Profile-style page title: big name, a badge, a quiet subtitle and optional actions. */
@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Tf.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = PageGutter, end = PageGutter, top = 18.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = TfType.display, color = c.ink, maxLines = 1)
                if (badge != null) {
                    Spacer(Modifier.width(12.dp))
                    Badge(badge)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = TfType.body, color = c.inkSecondary)
        }
        actions()
    }
}

/** A thin banner shown on every page while a routine is armed or running. */
@Composable
fun RunBanner(status: RunStatus, onStop: () -> Unit, modifier: Modifier = Modifier) {
    if (status.phase == Phase.Idle) return
    val c = Tf.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter, vertical = 4.dp)
            .background(c.accentSoft, CircleShape)
            .padding(PaddingValues(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(c.accent, pulse = status.active)
        Spacer(Modifier.width(10.dp))
        Text(
            when (status.phase) {
                Phase.Armed -> "Panel ready · press ▶ in the app you want"
                Phase.Countdown -> "Starting in ${status.countdown}…"
                Phase.Running -> "Running · ${status.gestures} taps"
                Phase.Paused -> "Paused"
                Phase.Finished -> "Finished · ${status.gestures} taps"
                Phase.Idle -> ""
            },
            style = TfType.label,
            color = c.ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        GlassChip(text = if (status.active) "Stop" else "Close", onClick = onStop, height = 36.dp)
    }
}
