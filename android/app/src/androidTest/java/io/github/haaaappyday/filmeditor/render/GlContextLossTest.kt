package io.github.haaaappyday.filmeditor.render

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.core.EditParams
import io.github.haaaappyday.filmeditor.core.EditorErrorCode
import io.github.haaaappyday.filmeditor.core.EditorException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GlContextLossTest {
    @Test fun injectedContextLossRetriesOnceWithFreshRealContextThenRemainsUsable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val presets = AssetPresetRepository(context)
        val lostContexts = AtomicInteger(0)
        val creations = AtomicInteger(0)
        // A real EGL context exists before shader loading. Inject the same typed
        // signal as EGL_CONTEXT_LOST at that boundary; retry must create a new one.
        val renderer = GlFilmRenderer(presets::loadCube, { path ->
            if (path.endsWith("film.vert")) {
                creations.incrementAndGet()
                if (lostContexts.getAndUpdate { maxOf(0, it - 1) } > 0) throw GlFailure("Injected EGL_CONTEXT_LOST", true)
            }
            context.assets.open(path).bufferedReader().use { it.readText() }
        }, 17, null)
        val reference = GlFilmRenderer(context, tileEdge = 17)
        val source = Bitmap.createBitmap(35, 29, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff7193b4.toInt()) }
        val params = EditParams("kodak_portra_400", .5f, .7f, 43f)
        try {
            lostContexts.set(1)
            val recovered = renderer.render(source, params)
            val expected = reference.render(source, params)
            try { assertTrue(expected.sameAs(recovered)) } finally { recovered.recycle(); expected.recycle() }
            assertEquals(2, creations.get())
            assertTrue(renderer.diagnostics().getValue("version").contains("OpenGL ES 3"))
            renderer.resetContextForTest()
            lostContexts.set(2)
            try { renderer.render(source, params).also { it.recycle() }; fail("Repeated loss must surface a recoverable error") }
            catch (error: EditorException) { assertEquals(EditorErrorCode.RENDER_FAILED, error.code) }
            assertEquals("One retry only, no unbounded retry loop", 4, creations.get())
            assertFalse(source.isRecycled)
            renderer.render(source, params).recycle()
            assertEquals(5, creations.get())
        } finally { renderer.release(); reference.release(); source.recycle() }
    }
}
