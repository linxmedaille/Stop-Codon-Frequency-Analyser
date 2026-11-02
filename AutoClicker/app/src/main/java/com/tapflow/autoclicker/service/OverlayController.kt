package com.tapflow.autoclicker.service

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tapflow.core.Marker
import kotlin.math.roundToInt

/**
 * Two accessibility-overlay windows:
 *  - the floating control panel (touchable, draggable), and
 *  - a full-screen, touch-through layer that shows where taps will land.
 */
class OverlayController(private val service: ClickerService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var panel: ComposeView? = null
    private var panelOwner: OverlayOwner? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var markersView: ComposeView? = null
    private var markersOwner: OverlayOwner? = null
    private val markers = mutableStateOf<List<Marker>>(emptyList())

    fun showPanel() {
        if (panel != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = service.resources.displayMetrics
            x = (dm.widthPixels * 0.06f).roundToInt()
            y = (dm.heightPixels * 0.12f).roundToInt()
        }
        val owner = OverlayOwner()
        val view = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                OverlayPanel(
                    onPlay = { service.play() },
                    onPause = { service.pause() },
                    onStop = { service.stop() },
                    onClose = { service.disarm() },
                    onOpenApp = { service.openApp() },
                    onToggleMarkers = { service.toggleMarkers() },
                    onDrag = { dx, dy -> movePanel(dx, dy) },
                )
            }
        }
        owner.attach(view)
        wm.addView(view, params)
        panel = view
        panelOwner = owner
        panelParams = params
    }

    private fun movePanel(dx: Float, dy: Float) {
        val view = panel ?: return
        val params = panelParams ?: return
        val dm = service.resources.displayMetrics
        params.x = (params.x + dx.roundToInt()).coerceIn(-view.width / 2, dm.widthPixels - view.width / 2)
        params.y = (params.y + dy.roundToInt()).coerceIn(0, dm.heightPixels - view.height / 2)
        runCatching { wm.updateViewLayout(view, params) }
    }

    fun showMarkers(list: List<Marker>) {
        markers.value = list
        if (markersView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        }
        val owner = OverlayOwner()
        val view = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { MarkersLayer(markers.value) }
        }
        owner.attach(view)
        // The panel must stay above the markers layer.
        val hadPanel = panel != null
        if (hadPanel) removePanelOnly()
        wm.addView(view, params)
        markersView = view
        markersOwner = owner
        if (hadPanel) showPanel()
    }

    private fun removePanelOnly() {
        panel?.let { runCatching { wm.removeView(it) } }
        panelOwner?.destroy()
        panel = null
        panelOwner = null
    }

    fun hideAll() {
        removePanelOnly()
        markersView?.let { runCatching { wm.removeView(it) } }
        markersOwner?.destroy()
        markersView = null
        markersOwner = null
        markers.value = emptyList()
    }
}

/** Lifecycle plumbing so Compose can run inside a window that has no Activity. */
@SuppressLint("RestrictedApi")
internal class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun attach(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
