package com.tapflow.autoclicker.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tapflow.autoclicker.data.Store
import com.tapflow.autoclicker.service.ClickerBridge
import com.tapflow.autoclicker.service.ProgramFactory
import com.tapflow.core.Board
import com.tapflow.core.Mode
import com.tapflow.core.ProgramBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Requests for the glass bottom sheet. */
sealed interface SheetRequest {
    data class Duration(
        val title: String,
        val valueMs: Long,
        val minMs: Long = 0,
        val maxMs: Long = 24L * 3_600_000,
        val presets: List<Long> = listOf(50, 100, 250, 500, 1_000, 2_000, 5_000, 20_000, 60_000),
        val note: String? = null,
        val onSet: (Long) -> Unit,
    ) : SheetRequest

    data class Count(
        val title: String,
        val value: Int,
        val min: Int = 1,
        val max: Int = 1_000_000,
        val presets: List<Int> = listOf(2, 3, 5, 10, 25, 50, 100, 1_000),
        /** Offer "forever", reported as 0. */
        val allowForever: Boolean = false,
        val suffix: String = "times",
        val onSet: (Int) -> Unit,
    ) : SheetRequest

    data class PickTarget(
        val title: String,
        val board: Board,
        val selected: String?,
        val onPick: (String) -> Unit,
        val onEditPoints: () -> Unit,
    ) : SheetRequest

    data object Permission : SheetRequest
}

data class Toast(val message: String, val isError: Boolean = false, val id: Long = System.nanoTime())

/** UI-wide state that isn't persisted: open sheet, toast, full-screen editor. */
@Stable
class AppUi(val store: Store, private val scope: CoroutineScope) {
    var sheet by mutableStateOf<SheetRequest?>(null)
    var toast by mutableStateOf<Toast?>(null)
    var editor by mutableStateOf<Mode?>(null)

    /** Set by the activity: launches the system photo picker for a mode's board. */
    var pickScreenshot: (Mode) -> Unit = {}

    fun show(request: SheetRequest) {
        sheet = request
    }

    fun dismissSheet() {
        sheet = null
    }

    fun toast(message: String, isError: Boolean = false) {
        toast = Toast(message, isError)
    }

    fun onScreenshotPicked(mode: Mode, uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val shot = store.importScreenshot(uri)
            if (shot == null) {
                toast("Couldn't open that image.", isError = true)
                return@launch
            }
            store.updateBoard(mode) { it.copy(screenshot = shot) }
            val screen = ClickerBridge.realScreen(store.context)
            val screenAspect = screen.width.toFloat() / screen.height
            val ratio = shot.aspect / screenAspect
            if (ratio < 0.97f || ratio > 1.03f) {
                val flipped = shot.aspect / (1f / screenAspect)
                if (flipped in 0.97f..1.03f) {
                    toast("Landscape screenshot: taps will map to landscape.")
                } else {
                    toast("This image isn't the shape of your screen; points are placed proportionally.")
                }
            }
            editor = mode
        }
    }

    /** Builds the routine for [mode] and hands it to the floating panel. */
    fun start(activity: Activity, mode: Mode) {
        if (!ClickerBridge.connected.value) {
            show(SheetRequest.Permission)
            return
        }
        val factory = ProgramFactory { screen ->
            val ws = store.workspace.value
            when (mode) {
                Mode.Single -> ProgramBuilder.single(ws.single, screen.orientedFor(ws.single.board))
                Mode.Multi -> ProgramBuilder.multi(ws.multi, screen.orientedFor(ws.multi.board))
                Mode.Chain -> ProgramBuilder.chain(ws.chain, screen.orientedFor(ws.chain.board))
            }
        }
        val result = ClickerBridge.arm(activity, factory)
        when {
            result == null -> show(SheetRequest.Permission)
            result.program == null -> toast(result.errors.firstOrNull()?.message ?: "Nothing to run yet.", isError = true)
            else -> {
                store.update { it.copy(lastMode = mode) }
                // Step aside so the user can open the app they want to automate.
                activity.moveTaskToBack(true)
            }
        }
    }

    companion object {
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }

        fun openAppInfo(context: Context) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}

/**
 * Screenshots fix the orientation the user designed for. If the screenshot is landscape
 * but the phone is currently portrait (or vice versa) at build time, we still map onto
 * the orientation that matches the screenshot, which is what the device will be in when
 * the target app (e.g. a landscape game) is in front.
 */
private fun com.tapflow.core.ScreenSpace.orientedFor(board: Board): com.tapflow.core.ScreenSpace {
    val shot = board.screenshot ?: return this
    val shotLandscape = shot.width > shot.height
    val screenLandscape = width > height
    return if (shotLandscape != screenLandscape) com.tapflow.core.ScreenSpace(height, width) else this
}
