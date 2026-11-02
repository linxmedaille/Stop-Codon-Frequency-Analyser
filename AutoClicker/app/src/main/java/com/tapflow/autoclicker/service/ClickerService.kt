package com.tapflow.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.tapflow.autoclicker.MainActivity
import com.tapflow.autoclicker.TapflowApp
import com.tapflow.core.FinishReason
import com.tapflow.core.GesturePerformer
import com.tapflow.core.PxPoint
import com.tapflow.core.RUNS_LOOP_ID
import com.tapflow.core.RunEvent
import com.tapflow.core.ScriptRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Performs the gestures. Android only lets accessibility services inject touches into
 * other apps, which is why an auto clicker needs one. It also hosts the floating panel
 * and the on-screen markers (accessibility overlays need no extra permission).
 */
class ClickerService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlays: OverlayController? = null
    private var factory: ProgramFactory? = null
    private var runJob: Job? = null
    private val paused = MutableStateFlow(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlays = OverlayController(this)
        ClickerBridge.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        runJob?.cancel()
        overlays?.hideAll()
        overlays = null
        if (ClickerBridge.service === this) ClickerBridge.service = null
        ClickerBridge._status.value = RunStatus()
        scope.cancel()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation changes pixel positions; refresh markers when not mid-run.
        if (!ClickerBridge.status.value.active) refreshMarkers()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val prefs = TapflowApp.store.workspace.value.prefs
        if (prefs.volumeKeyStop &&
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
            ClickerBridge.status.value.active
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) stop()
            return true
        }
        return super.onKeyEvent(event)
    }

    // ---- Commands -------------------------------------------------------------------

    fun arm(factory: ProgramFactory) {
        runJob?.cancel()
        this.factory = factory
        paused.value = false
        val title = factory.build(ClickerBridge.realScreen(this)).program?.title.orEmpty()
        ClickerBridge._status.value = RunStatus(phase = Phase.Armed, title = title)
        overlays?.showPanel()
        refreshMarkers()
    }

    fun disarm() {
        runJob?.cancel()
        factory = null
        ClickerBridge._status.value = RunStatus()
        overlays?.hideAll()
    }

    fun pause() {
        if (ClickerBridge.status.value.phase == Phase.Running) {
            paused.value = true
            ClickerBridge._status.update { it.copy(phase = Phase.Paused) }
        }
    }

    fun stop() {
        val wasActive = ClickerBridge.status.value.active
        runJob?.cancel()
        runJob = null
        paused.value = false
        if (wasActive) {
            ClickerBridge._status.update { it.copy(phase = Phase.Finished, finishReason = FinishReason.Stopped, currentBlock = null) }
        }
    }

    fun play() {
        val status = ClickerBridge.status.value
        if (status.phase == Phase.Paused) {
            paused.value = false
            ClickerBridge._status.update { it.copy(phase = Phase.Running) }
            return
        }
        if (status.active) return
        val f = factory ?: return
        runJob?.cancel()
        paused.value = false
        runJob = scope.launch { runRoutine(f) }
    }

    fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(intent)
    }

    fun toggleMarkers() {
        TapflowApp.store.update { it.copy(prefs = it.prefs.copy(showMarkers = !it.prefs.showMarkers)) }
        refreshMarkers()
    }

    // ---- Running --------------------------------------------------------------------

    private fun refreshMarkers() {
        val f = factory ?: return
        val program = f.build(ClickerBridge.realScreen(this)).program
        val show = TapflowApp.store.workspace.value.prefs.showMarkers
        overlays?.showMarkers(if (show) program?.markers.orEmpty() else emptyList())
    }

    private suspend fun runRoutine(f: ProgramFactory) {
        val countdown = TapflowApp.store.workspace.value.prefs.countdownSec
        for (n in countdown downTo 1) {
            ClickerBridge._status.update {
                it.copy(phase = Phase.Countdown, countdown = n, gestures = 0, elapsedMs = 0, loop = 0, loopTimes = 0, finishReason = null, message = null)
            }
            delay(1_000)
        }
        val result = f.build(ClickerBridge.realScreen(this))
        val program = result.program
        if (program == null) {
            ClickerBridge._status.update {
                it.copy(phase = Phase.Finished, message = result.errors.firstOrNull()?.message ?: "Nothing to run.")
            }
            return
        }
        refreshMarkers()
        val gestures = AtomicLong(0)
        ClickerBridge._status.update {
            it.copy(phase = Phase.Running, title = program.title, countdown = 0, gestures = 0, elapsedMs = 0, finishReason = null, message = null)
        }
        val runner = ScriptRunner(GesturePerformerImpl(), paused)
        val ticker = scope.launch {
            while (isActive) {
                val stats = runner.stats()
                ClickerBridge._status.update { it.copy(gestures = gestures.get(), elapsedMs = stats.activeMs) }
                delay(200)
            }
        }
        try {
            runner.run(program) { event ->
                when (event) {
                    is RunEvent.Gesture -> {
                        gestures.incrementAndGet()
                        ClickerBridge._flashes.tryEmit(GestureFlash(event.at, event.to))
                        if (ClickerBridge.status.value.currentBlock != event.sourceId) {
                            ClickerBridge._status.update { it.copy(currentBlock = event.sourceId) }
                        }
                    }
                    is RunEvent.Loop -> if (event.sourceId == RUNS_LOOP_ID) {
                        ClickerBridge._status.update { it.copy(loop = event.iteration, loopTimes = event.times) }
                    }
                    is RunEvent.Waiting -> if (event.sourceId != null && ClickerBridge.status.value.currentBlock != event.sourceId) {
                        ClickerBridge._status.update { it.copy(currentBlock = event.sourceId) }
                    }
                    is RunEvent.Finished -> {
                        ClickerBridge._status.update {
                            it.copy(
                                phase = Phase.Finished,
                                finishReason = event.reason,
                                gestures = event.stats.gestures,
                                elapsedMs = event.stats.activeMs,
                                currentBlock = null,
                            )
                        }
                    }
                }
            }
        } finally {
            ticker.cancel()
            paused.value = false
        }
    }

    private inner class GesturePerformerImpl : GesturePerformer {
        override suspend fun tap(at: PxPoint, holdMs: Long): Boolean =
            dispatch(Path().apply { moveTo(at.x, at.y) }, holdMs)

        override suspend fun swipe(from: PxPoint, to: PxPoint, durationMs: Long): Boolean =
            dispatch(Path().apply { moveTo(from.x, from.y); lineTo(to.x, to.y) }, durationMs)

        private suspend fun dispatch(path: Path, durationMs: Long): Boolean =
            suspendCancellableCoroutine { cont ->
                val stroke = GestureDescription.StrokeDescription(
                    path, 0, durationMs.coerceIn(1, GestureDescription.getMaxGestureDuration()),
                )
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val accepted = dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                    null,
                )
                if (!accepted && cont.isActive) cont.resume(false)
            }
    }
}
