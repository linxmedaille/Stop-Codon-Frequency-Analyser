package com.tapflow.autoclicker.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.autoclicker.data.Store
import com.tapflow.autoclicker.ui.AppUi
import com.tapflow.autoclicker.ui.components.PageGutter
import com.tapflow.autoclicker.ui.components.PageHeader
import com.tapflow.autoclicker.ui.components.PulsingDot
import com.tapflow.autoclicker.ui.glass.GlassCard
import com.tapflow.autoclicker.ui.glass.GlassChip
import com.tapflow.autoclicker.ui.glass.GlassSegmented
import com.tapflow.autoclicker.ui.glass.GlassSwitch
import com.tapflow.autoclicker.ui.glass.Hairline
import com.tapflow.autoclicker.ui.glass.IconTile
import com.tapflow.autoclicker.ui.glass.Kicker
import com.tapflow.autoclicker.ui.glass.SettingRow
import com.tapflow.autoclicker.ui.theme.Tf
import com.tapflow.autoclicker.ui.theme.TfType
import com.tapflow.core.Preferences
import com.tapflow.core.ThemeMode

private fun Store.updatePrefs(transform: (Preferences) -> Preferences) = update { it.copy(prefs = transform(it.prefs)) }

@Composable
fun SettingsPage(store: Store, serviceOn: Boolean) {
    val ws by store.workspace.collectAsStateWithLifecycle()
    val prefs = ws.prefs
    val c = Tf.colors
    val context = LocalContext.current

    PageScroll {
        PageHeader(title = "Settings", subtitle = "Tune how Tapflow behaves")

        GlassCard(Modifier.padding(horizontal = PageGutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.AccessibilityNew, if (serviceOn) c.success else c.warning, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Accessibility service", style = TfType.title, color = c.ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulsingDot(if (serviceOn) c.success else c.warning, pulse = !serviceOn)
                        Spacer(Modifier.width(8.dp))
                        Text(if (serviceOn) "On and ready" else "Off: needed to tap other apps", style = TfType.label, color = c.inkSecondary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row {
                GlassChip(
                    if (serviceOn) "Manage" else "Turn on",
                    selected = !serviceOn,
                    onClick = { AppUi.openAccessibilitySettings(context) },
                )
                Spacer(Modifier.width(10.dp))
                GlassChip("App info", onClick = { AppUi.openAppInfo(context) })
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Top) {
                androidx.compose.material3.Icon(Icons.Rounded.Lock, null, tint = c.inkTertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "The service only performs the gestures you set up. It doesn't read the screen and Tapflow has no internet permission.",
                    style = TfType.caption,
                    color = c.inkSecondary,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        GlassCard(Modifier.padding(horizontal = PageGutter)) {
            Kicker("While running")
            Spacer(Modifier.height(6.dp))
            SettingRow(
                "Show tap markers",
                subtitle = "Numbered rings where taps land, with a ripple per tap",
                leading = { IconTile(Icons.Rounded.CenterFocusStrong, Color(0xFF7B5CFF)) },
            ) { GlassSwitch(prefs.showMarkers, { v -> store.updatePrefs { it.copy(showMarkers = v) } }) }
            Hairline(Modifier.padding(vertical = 6.dp))
            SettingRow(
                "Volume-down stops",
                subtitle = "A panic button that works in any app",
                leading = { IconTile(Icons.Rounded.VolumeDown, Color(0xFFE5484D)) },
            ) { GlassSwitch(prefs.volumeKeyStop, { v -> store.updatePrefs { it.copy(volumeKeyStop = v) } }) }
            Hairline(Modifier.padding(vertical = 6.dp))
            Text("Countdown after pressing ▶", style = TfType.label.copy(fontSize = TfType.body.fontSize), color = c.ink, modifier = Modifier.padding(vertical = 10.dp))
            GlassSegmented(
                options = listOf(0, 3, 5, 10),
                selected = prefs.countdownSec,
                onSelect = { v -> store.updatePrefs { it.copy(countdownSec = v) } },
                label = { if (it == 0) "None" else "$it s" },
            )
        }

        Spacer(Modifier.height(14.dp))
        GlassCard(Modifier.padding(horizontal = PageGutter)) {
            Kicker("Look & feel")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                IconTile(Icons.Rounded.DarkMode, Color(0xFF3A3A3C))
                Spacer(Modifier.width(14.dp))
                Text("Appearance", style = TfType.label.copy(fontSize = TfType.body.fontSize), color = c.ink)
            }
            GlassSegmented(
                options = ThemeMode.entries.toList(),
                selected = prefs.theme,
                onSelect = { v -> store.updatePrefs { it.copy(theme = v) } },
                label = { it.name },
            )
            Spacer(Modifier.height(8.dp))
            SettingRow(
                "Haptics",
                leading = { IconTile(Icons.Rounded.Vibration, Color(0xFF16B9A0)) },
            ) { GlassSwitch(prefs.haptics, { v -> store.updatePrefs { it.copy(haptics = v) } }) }
            Hairline(Modifier.padding(vertical = 6.dp))
            SettingRow(
                "Reduce motion",
                subtitle = "Keep the background still",
                leading = { IconTile(Icons.Rounded.Animation, Color(0xFFFF9F43)) },
            ) { GlassSwitch(prefs.reduceMotion, { v -> store.updatePrefs { it.copy(reduceMotion = v) } }) }
        }

        Spacer(Modifier.height(14.dp))
        GlassCard(Modifier.padding(horizontal = PageGutter)) {
            Kicker("How it works")
            Spacer(Modifier.height(10.dp))
            Text("Set it up here, run it anywhere.", style = TfType.editorial, color = c.ink)
            Spacer(Modifier.height(14.dp))
            HowStep(1, "Take a screenshot of the app you want to automate.")
            HowStep(2, "Add it to a mode and place your points on it.")
            HowStep(3, "Press Start. Tapflow steps aside and shows a floating panel.")
            HowStep(4, "Open your app and press ▶ on the panel. ■ or volume-down stops.")
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Tapflow 1.0",
            style = TfType.caption,
            color = c.inkTertiary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter),
        )
    }
}

@Composable
private fun HowStep(n: Int, text: String) {
    val c = Tf.colors
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).background(c.chip, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", style = TfType.caption.copy(fontWeight = FontWeight.Bold), color = c.onChip)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = TfType.body, color = c.ink, modifier = Modifier.padding(top = 2.dp))
    }
}
