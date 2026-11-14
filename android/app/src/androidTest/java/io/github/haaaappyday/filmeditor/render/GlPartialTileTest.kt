package io.github.haaaappyday.filmeditor.render

import android.graphics.Bitmap
import android.opengl.GLES30 as GL
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.core.EditParams
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class GlPartialTileTest {
    @Test fun cancellationAfterFirstCompletedTileDeletesTransientGlObjectsAndAllowsNextRender() {
        // Instrumentation worker thread owns this real EGL session throughout.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = GlSession { context.assets.open(it).bufferedReader().use { reader -> reader.readText() } }
        val source = Bitmap.createBitmap(35, 29, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) {
                setPixel(x, y, 0xff000000.toInt() or ((x * 7) shl 16) or ((y * 8) shl 8) or (x + y))
            }
            setHasAlpha(false)
        }
        val original = IntArray(source.width * source.height).also { source.getPixels(it, 0, source.width, 0, 0, source.width, source.height) }
        val cube = Cube(2, floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f),
            FloatArray(24) { i -> val corner = i / 3; ((corner shr (i % 3)) and 1).toFloat() })
        var checks = 0
        var framebuffer = 0; var sourceTexture = 0; var targetTexture = 0
        fun integer(name: Int) = IntArray(1).also { GL.glGetIntegerv(name, it, 0) }[0]
        try {
            try {
                session.render(source, cube, EditParams("identity"), 17) {
                    // Check1 precedes allocation; check2 precedes tile1; check3
                    // follows real draw/readback/setPixels for tile1.
                    if (++checks == 3) {
                        framebuffer = integer(GL.GL_DRAW_FRAMEBUFFER_BINDING)
                        sourceTexture = integer(GL.GL_TEXTURE_BINDING_2D)
                        targetTexture = IntArray(1).also {
                            GL.glGetFramebufferAttachmentParameteriv(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0,
                                GL.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, it, 0)
                        }[0]
                        assertTrue(GL.glIsFramebuffer(framebuffer)); assertTrue(GL.glIsTexture(sourceTexture))
                        assertTrue(GL.glIsTexture(targetTexture))
                        throw CancellationException("Cancel after first tile")
                    }
                }.also { it.recycle(); fail("Partially rendered image must not be published") }
            } catch (_: CancellationException) { /* expected at the known tile boundary */ }
            assertEquals(3, checks)
            assertFalse(GL.glIsFramebuffer(framebuffer)); assertFalse(GL.glIsTexture(sourceTexture))
            assertFalse(GL.glIsTexture(targetTexture)); assertEquals(GL.GL_NO_ERROR, GL.glGetError())
            assertFalse(source.isRecycled)
            val actualSource = IntArray(original.size).also { source.getPixels(it, 0, source.width, 0, 0, source.width, source.height) }
            assertArrayEquals(original, actualSource)
            val next = session.render(source, cube, EditParams("identity"), 17) {}
            try {
                val pixels = IntArray(original.size).also { next.getPixels(it, 0, next.width, 0, 0, next.width, next.height) }
                assertArrayEquals(original, pixels)
            } finally { next.recycle() }
        } finally { session.close(); source.recycle() }
    }
}
