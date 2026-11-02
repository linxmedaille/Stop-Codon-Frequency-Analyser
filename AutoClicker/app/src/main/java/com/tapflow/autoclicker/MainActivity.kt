package com.tapflow.autoclicker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tapflow.autoclicker.ui.AppUi
import com.tapflow.autoclicker.ui.RootScreen
import com.tapflow.autoclicker.ui.SheetRequest
import com.tapflow.autoclicker.ui.theme.TapflowTheme
import com.tapflow.autoclicker.ui.theme.isDarkFor
import com.tapflow.core.Mode

class MainActivity : ComponentActivity() {
    private lateinit var ui: AppUi
    private var pickingFor: Mode = Mode.Single
    private var requestedPage by mutableStateOf<Pair<Int, Long>?>(null)

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        ui.onScreenshotPicked(pickingFor, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pickingFor = savedInstanceState?.getString(KEY_PICKING)?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.Single

        val store = TapflowApp.store
        ui = AppUi(store, lifecycleScope)
        ui.pickScreenshot = { mode ->
            pickingFor = mode
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        handleIntent(intent)

        setContent {
            val ws by store.workspace.collectAsStateWithLifecycle()
            val dark = isDarkFor(ws.prefs.theme)
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            TapflowTheme(dark = dark) {
                RootScreen(ui, pageRequest = requestedPage)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Optional deep links: `--ei page 0..3` opens a page, `--es editor Chain` opens a board. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.hasExtra(EXTRA_PAGE)) requestedPage = intent.getIntExtra(EXTRA_PAGE, 0) to System.nanoTime()
        intent.getStringExtra(EXTRA_EDITOR)?.let { name ->
            runCatching { Mode.valueOf(name) }.getOrNull()?.let { ui.editor = it }
        }
        // Used by the CI screenshot script; harmless otherwise.
        when (intent.getStringExtra(EXTRA_SHEET)) {
            "permission" -> ui.show(SheetRequest.Permission)
            "duration" -> ui.show(SheetRequest.Duration(title = "Wait how long?", valueMs = 20_000, onSet = {}))
            "count" -> ui.show(SheetRequest.Count(title = "Repeat how many times?", value = 5, allowForever = true, onSet = {}))
        }
        intent.getStringExtra(EXTRA_START)?.let { name ->
            runCatching { Mode.valueOf(name) }.getOrNull()?.let { mode ->
                window.decorView.post { ui.start(this, mode) }
            }
        }
        intent.removeExtra(EXTRA_SHEET)
        intent.removeExtra(EXTRA_START)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PICKING, pickingFor.name)
    }

    companion object {
        private const val KEY_PICKING = "picking_for"
        const val EXTRA_PAGE = "page"
        const val EXTRA_EDITOR = "editor"
        const val EXTRA_SHEET = "sheet"
        const val EXTRA_START = "start"
    }
}
