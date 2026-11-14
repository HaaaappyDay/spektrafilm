package io.github.haaaappyday.filmeditor.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import io.github.haaaappyday.filmeditor.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors

/** Owns one serialized offscreen GL context. release waits for active use and is terminal. */
class GlFilmRenderer private constructor(
    private val cubeProvider: suspend (String) -> Cube,
    private val shaderReader: (String) -> String,
    private val tileEdge: Int,
    private val onOutput: ((Bitmap) -> Unit)?,
) : FilmRenderer {
    constructor(context: Context, presets: AssetPresetRepository = AssetPresetRepository(context), tileEdge: Int = 1024) :
        this(presets::loadCube, { path -> context.applicationContext.assets.open(path).bufferedReader().use { it.readText() } }, tileEdge, null)

    internal constructor(context: Context, tileEdge: Int, cubeProvider: suspend (String) -> Cube, onOutput: ((Bitmap) -> Unit)? = null) :
        this(cubeProvider, { path -> context.applicationContext.assets.open(path).bufferedReader().use { it.readText() } }, tileEdge, onOutput)

    private val dispatcher = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "film-gl").apply { isDaemon = true } }.asCoroutineDispatcher()
    private val mutex = Mutex()
    private var released = false // under mutex
    private var session: GlSession? = null // only on dispatcher

    override suspend fun render(input: Bitmap, params: EditParams): Bitmap {
        var produced: Bitmap? = null
        try {
            ColorMath.validate(params)
            if (tileEdge !in 1..4096 || input.isRecycled || input.config != Bitmap.Config.ARGB_8888 ||
                input.colorSpace != ColorSpace.get(ColorSpace.Named.SRGB)) throw EditorException(EditorErrorCode.INVALID_PARAMS)
            if (input.width.toLong() * input.height > 4096L * 4096) throw EditorException(EditorErrorCode.IMAGE_TOO_LARGE)
            return mutex.withLock {
                if (released) throw EditorException(EditorErrorCode.RENDER_FAILED)
                withContext(dispatcher) {
                    val cube = cubeProvider(params.presetId)
                    val context = currentCoroutineContext()
                    context.ensureActive()
                    fun draw(): Bitmap {
                        val gl = session ?: GlSession(shaderReader).also { session = it }
                        return gl.render(input, cube, params, tileEdge) { context.ensureActive() }
                    }
                    produced = try { draw() } catch (error: GlFailure) {
                        session?.close(); session = null
                        if (error.contextLost) draw() else throw error
                    }
                    onOutput?.invoke(checkNotNull(produced))
                    context.ensureActive()
                    checkNotNull(produced)
                }
            }
        } catch (error: Throwable) {
            // Includes cancellation at withContext's return boundary: no caller owns it yet.
            produced?.recycle()
            when (error) {
                is CancellationException -> throw error
                is EditorException -> throw error
                is OutOfMemoryError -> throw EditorException(EditorErrorCode.IMAGE_TOO_LARGE, error)
                is Exception -> throw EditorException(EditorErrorCode.RENDER_FAILED, error)
                else -> throw error
            }
        }
    }

    override suspend fun release() = withContext(NonCancellable) {
        mutex.withLock {
            if (!released) {
                released = true
                try { withContext(dispatcher) { session?.close(); session = null } }
                finally { dispatcher.close() }
            }
        }
    }

    internal suspend fun diagnostics(): Map<String, String> = mutex.withLock {
        if (released) throw EditorException(EditorErrorCode.RENDER_FAILED)
        withContext(dispatcher) { session?.diagnostics.orEmpty() }
    }
    internal suspend fun resetContextForTest() = mutex.withLock {
        check(!released)
        withContext(dispatcher) { session?.close(); session = null }
    }
}
