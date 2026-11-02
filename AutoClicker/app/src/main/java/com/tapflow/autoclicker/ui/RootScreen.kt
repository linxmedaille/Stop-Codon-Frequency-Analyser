package com.tapflow.autoclicker.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.autoclicker.service.ClickerBridge
import com.tapflow.autoclicker.ui.board.BoardEditor
import com.tapflow.autoclicker.ui.chain.ChainPage
import com.tapflow.autoclicker.ui.chain.ChainTray
import com.tapflow.autoclicker.ui.chain.ChainUiState
import com.tapflow.autoclicker.ui.components.SheetHost
import com.tapflow.autoclicker.ui.components.ToastHost
import com.tapflow.autoclicker.ui.dock.DockItem
import com.tapflow.autoclicker.ui.dock.LiquidDock
import com.tapflow.autoclicker.ui.glass.Aurora
import com.tapflow.autoclicker.ui.glass.GlassDefaults
import com.tapflow.autoclicker.ui.glass.LocalBackdrop
import com.tapflow.autoclicker.ui.glass.LocalHapticsEnabled
import com.tapflow.autoclicker.ui.glass.backdropSource
import com.tapflow.autoclicker.ui.glass.liquidGlass
import com.tapflow.autoclicker.ui.glass.rememberBackdrop
import com.tapflow.autoclicker.ui.pages.MultiPage
import com.tapflow.autoclicker.ui.pages.SettingsPage
import com.tapflow.autoclicker.ui.pages.SinglePage
import com.tapflow.autoclicker.ui.theme.Tf
import com.tapflow.core.BlockTree
import com.tapflow.core.Mode
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

private const val PAGE_COUNT = 4
private const val SETTINGS_PAGE = 3

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun RootScreen(ui: AppUi, initialPage: Int? = null) {
    val store = ui.store
    val ws by store.workspace.collectAsStateWithLifecycle()
    val connected by ClickerBridge.connected.collectAsStateWithLifecycle()
    val status by ClickerBridge.status.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val scope = rememberCoroutineScope()

    val bgBackdrop = rememberBackdrop()
    val sceneBackdrop = rememberBackdrop()
    val pager = rememberPagerState(initialPage = initialPage ?: ws.lastMode.ordinal) { PAGE_COUNT }
    val chainState = remember { ChainUiState() }

    LaunchedEffect(initialPage) {
        if (initialPage != null && initialPage != pager.currentPage) pager.scrollToPage(initialPage.coerceIn(0, PAGE_COUNT - 1))
    }

    val position = { pager.currentPage + pager.currentPageOffsetFraction }
    val start: (Mode) -> Unit = { mode -> activity?.let { ui.start(it, mode) } }
    val stop: () -> Unit = {
        if (status.active) ClickerBridge.stop() else ClickerBridge.disarm()
    }
    val toSettings: () -> Unit = { scope.launch { pager.animateScrollToPage(SETTINGS_PAGE, animationSpec = spring(0.8f, 260f)) } }

    val items = listOf(
        DockItem("Single", Icons.Rounded.TouchApp),
        DockItem("Multi", Icons.Rounded.Apps),
        DockItem("Chain", Icons.Rounded.Extension),
        DockItem("Settings", Icons.Rounded.Tune, badge = if (connected) null else "!"),
    )

    CompositionLocalProvider(
        LocalBackdrop provides bgBackdrop,
        LocalHapticsEnabled provides ws.prefs.haptics,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Everything the dock and sheets refract.
            Box(Modifier.fillMaxSize().backdropSource(sceneBackdrop)) {
                Aurora(
                    pagePosition = position,
                    modifier = Modifier.fillMaxSize().backdropSource(bgBackdrop),
                    animate = !ws.prefs.reduceMotion,
                )
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { page ->
                    LiquidPage(pager, page) {
                        when (page) {
                            0 -> SinglePage(store, ui, connected, status, { start(Mode.Single) }, stop, toSettings)
                            1 -> MultiPage(store, ui, connected, status, { start(Mode.Multi) }, stop, toSettings)
                            2 -> ChainPage(store, ui, chainState, connected, status, { start(Mode.Chain) }, stop, toSettings)
                            else -> SettingsPage(store, connected)
                        }
                    }
                }
            }

            // Block palette, floating above the dock on the Chain page only.
            val trayShown by remember { derivedStateOf { abs(position() - 2f) < 0.98f } }
            if (trayShown) {
                ChainTray(
                    store = store,
                    state = chainState,
                    backdrop = sceneBackdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp + 74.dp + 12.dp)
                        .graphicsLayer {
                            val v = (1f - abs(position() - 2f)).coerceIn(0f, 1f)
                            alpha = v
                            translationY = (1f - v) * 36.dp.toPx()
                            scaleX = lerp(0.92f, 1f, v)
                            scaleY = lerp(0.92f, 1f, v)
                        },
                )
            }

            LiquidDock(
                items = items,
                pager = pager,
                backdrop = sceneBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            ToastHost(ui, sceneBackdrop, Modifier.align(Alignment.TopCenter))
            SheetHost(ui, sceneBackdrop)

            val editorMode = ui.editor
            AnimatedVisibility(
                visible = editorMode != null,
                enter = fadeIn(tween(200)) + scaleIn(spring(0.82f, 380f), initialScale = 0.9f),
                exit = fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 0.94f),
            ) {
                val mode = remember { editorMode ?: Mode.Single }
                val board = when (mode) {
                    Mode.Single -> ws.single.board
                    Mode.Multi -> ws.multi.board
                    Mode.Chain -> ws.chain.board
                }
                BoardEditor(
                    mode = mode,
                    board = board,
                    onBoardChange = { f -> store.updateBoard(mode) { b -> f(b) } },
                    onRemoveTarget = { id ->
                        store.update { w ->
                            when (mode) {
                                Mode.Single -> w.copy(single = w.single.copy(board = w.single.board.removeTarget(id)))
                                Mode.Multi -> w.copy(multi = w.multi.copy(board = w.multi.board.removeTarget(id), delays = w.multi.delays - id))
                                Mode.Chain -> w.copy(
                                    chain = w.chain.copy(
                                        board = w.chain.board.removeTarget(id),
                                        blocks = BlockTree.clearTarget(w.chain.blocks, id),
                                    ),
                                )
                            }
                        }
                    },
                    onPickScreenshot = { ui.pickScreenshot(mode) },
                    onClose = { ui.editor = null },
                )
            }
        }
    }
}

private fun pageOffset(pager: PagerState, page: Int): Float =
    (pager.currentPage - page) + pager.currentPageOffsetFraction

/**
 * Wraps each page so that, while it's being swiped, it turns into a sheet of liquid
 * glass: it shrinks a little, its corners round off, a frosted slab with refracting
 * edges fades in behind the content, and it slides with a touch of parallax.
 */
@Composable
private fun LiquidPage(pager: PagerState, page: Int, content: @Composable () -> Unit) {
    val c = Tf.colors
    val moving by remember(page) { derivedStateOf { abs(pageOffset(pager, page)) > 0.001f } }
    val corner = 44.dp
    val slabStyle = remember(c) {
        GlassDefaults.clear(c).copy(
            tint = if (c.isDark) c.glass else c.glass.copy(alpha = 0.35f),
            blur = 26.dp,
            refraction = 1f,
            bezel = 30.dp,
            sheen = if (c.isDark) 0.06f else 0.18f,
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val o = pageOffset(pager, page)
                val a = abs(o).coerceIn(0f, 1f)
                val s = lerp(1f, 0.88f, a)
                scaleX = s
                scaleY = s
                translationX = o * size.width * 0.10f
                alpha = lerp(1f, 0.25f, a * a)
                val r = corner.toPx() * min(1f, a * 4f)
                shape = RoundedCornerShape(r)
                clip = a > 0.0005f
            },
    ) {
        if (moving) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = min(1f, abs(pageOffset(pager, page)) * 5f) }
                    .liquidGlass(LocalBackdrop.current, RoundedCornerShape(corner), slabStyle),
            )
        }
        content()
    }
}
