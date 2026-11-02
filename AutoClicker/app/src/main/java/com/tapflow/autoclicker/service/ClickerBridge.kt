package com.tapflow.autoclicker.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.WindowManager
import com.tapflow.core.BuildResult
import com.tapflow.core.FinishReason
import com.tapflow.core.PxPoint
import com.tapflow.core.ScreenSpace
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Phase { Idle, Armed, Countdown, Running, Paused, Finished }

/** What the floating panel and the app show about the current routine. */
data class RunStatus(
    val phase: Phase = Phase.Idle,
    val title: String = "",
    val countdown: Int = 0,
    val gestures: Long = 0,
    val elapsedMs: Long = 0,
    val loop: Int = 0,
    val loopTimes: Int = 0,
    val currentBlock: String? = null,
    val finishReason: FinishReason? = null,
    val message: String? = null,
) {
    val active: Boolean get() = phase == Phase.Countdown || phase == Phase.Running || phase == Phase.Paused
}

/** Builds the program against the real screen at the moment play is pressed. */
fun interface ProgramFactory {
    fun build(screen: ScreenSpace): BuildResult
}

data class GestureFlash(val at: PxPoint, val to: PxPoint?)

/**
 * The app and the accessibility service live in the same process; this object is how
 * they talk. The service registers itself when the user enables it.
 */
object ClickerBridge {
    internal var service: ClickerService? = null
        set(value) {
            field = value
            _connected.value = value != null
        }

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    internal val _status = MutableStateFlow(RunStatus())
    val status: StateFlow<RunStatus> = _status.asStateFlow()

    internal val _flashes = MutableSharedFlow<GestureFlash>(extraBufferCapacity = 64)
    val flashes: SharedFlow<GestureFlash> = _flashes.asSharedFlow()

    /**
     * Shows the floating panel with [factory] loaded. The routine starts when the user
     * presses play on the panel (so they can switch to the target app first).
     * Returns the build result for the current screen so the caller can show errors.
     */
    fun arm(context: Context, factory: ProgramFactory): BuildResult? {
        val s = service ?: return null
        val result = factory.build(realScreen(context))
        if (result.program == null) return result
        s.arm(factory)
        return result
    }

    fun play() = service?.play()
    fun pause() = service?.pause()
    fun stop() = service?.stop()
    fun disarm() = service?.disarm()

    /** Whether the user has switched the service on in system settings. */
    fun isEnabledInSettings(context: Context): Boolean {
        val expected = ComponentName(context, ClickerService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (component in splitter) {
            if (ComponentName.unflattenFromString(component) == expected) return true
        }
        return false
    }

    /** The full physical display in its current orientation, in pixels. */
    fun realScreen(context: Context): ScreenSpace {
        val wm = context.getSystemService(WindowManager::class.java)
        return if (android.os.Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            ScreenSpace(b.width(), b.height())
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            ScreenSpace(dm.widthPixels, dm.heightPixels)
        }
    }
}
