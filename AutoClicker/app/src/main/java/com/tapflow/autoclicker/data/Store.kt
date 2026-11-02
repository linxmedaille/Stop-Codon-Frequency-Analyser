package com.tapflow.autoclicker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tapflow.core.Board
import com.tapflow.core.Mode
import com.tapflow.core.Screenshot
import com.tapflow.core.Workspace
import com.tapflow.core.WorkspaceCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * The single source of truth for everything the user sets up. Changes are kept in memory
 * and written to disk shortly after (debounced), so dragging a slider doesn't hammer the
 * file system.
 */
class Store(context: Context) {
    private val appContext = context.applicationContext
    val context: Context get() = appContext
    private val file = File(appContext.filesDir, "workspace.json")
    private val screensDir = File(appContext.filesDir, "screens").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private val _workspace = MutableStateFlow(load())
    val workspace: StateFlow<Workspace> = _workspace.asStateFlow()

    private fun load(): Workspace = WorkspaceCodec.decode(runCatching { file.readText() }.getOrNull())

    fun update(transform: (Workspace) -> Workspace) {
        _workspace.update(transform)
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(250)
            saveNow()
        }
    }

    private fun saveNow() {
        val text = WorkspaceCodec.encode(_workspace.value)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
        cleanUpScreens()
    }

    fun board(mode: Mode): Board = when (mode) {
        Mode.Single -> _workspace.value.single.board
        Mode.Multi -> _workspace.value.multi.board
        Mode.Chain -> _workspace.value.chain.board
    }

    fun updateBoard(mode: Mode, transform: (Board) -> Board) = update { ws ->
        when (mode) {
            Mode.Single -> ws.copy(single = ws.single.copy(board = transform(ws.single.board)))
            Mode.Multi -> ws.copy(multi = ws.multi.copy(board = transform(ws.multi.board)))
            Mode.Chain -> ws.copy(chain = ws.chain.copy(board = transform(ws.chain.board)))
        }
    }

    /**
     * Copies the picked image into private storage (downscaled if it's huge) and returns
     * its description. Runs off the main thread.
     */
    suspend fun importScreenshot(uri: Uri): Screenshot? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = appContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@runCatching null
            var sample = 1
            while (maxOf(w, h) / (sample * 2) >= 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching null
            val out = File(screensDir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            ScreenshotCache.put(out.absolutePath, bitmap.asImageBitmap())
            // Keep the original dimensions: they define the aspect ratio of the screen.
            Screenshot(path = out.absolutePath, width = w, height = h)
        }.getOrNull()
    }

    /** Deletes screenshot files no board refers to any more. */
    private fun cleanUpScreens() {
        val ws = _workspace.value
        val used = listOfNotNull(
            ws.single.board.screenshot?.path,
            ws.multi.board.screenshot?.path,
            ws.chain.board.screenshot?.path,
        ).toSet()
        screensDir.listFiles()?.forEach { f ->
            if (f.absolutePath !in used && System.currentTimeMillis() - f.lastModified() > 10_000) f.delete()
        }
    }
}

/** Decoded screenshots, shared by previews and the full-screen editor. */
object ScreenshotCache {
    private val cache = object : LruCache<String, ImageBitmap>(4) {}

    fun get(path: String): ImageBitmap? = cache.get(path)

    fun put(path: String, bitmap: ImageBitmap) {
        cache.put(path, bitmap)
    }

    suspend fun load(path: String): ImageBitmap? {
        get(path)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }?.also { put(path, it) }
    }
}
