package io.github.haaaappyday.filmeditor.render

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PresetThumbnailRendererTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets
    private val repository = AssetPresetRepository(instrumentation.targetContext)
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    // A wrong film, strength or color transform must disagree with the committed Python bytes.
    @Test fun allFilmCardsUseIndependentDefaultFilmReferenceWithoutUpscalingOrChangingSource() = runBlocking {
        val meta = JSONObject(assets.open("render-fixtures/reference.json").bufferedReader().use { it.readText() })
        val bytes = assets.open("render-fixtures/input.rgba").use { it.readBytes() }
        val original = IntArray(bytes.size / 4) { i ->
            (255 shl 24) or ((bytes[4*i].toInt() and 255) shl 16) or
                ((bytes[4*i+1].toInt() and 255) shl 8) or (bytes[4*i+2].toInt() and 255)
        }
        val input = Bitmap.createBitmap(original, meta.getInt("width"), meta.getInt("height"), Bitmap.Config.ARGB_8888)
        val films = repository.listPresets()
        var results = emptyMap<String, Bitmap>()
        try {
            results = PresetThumbnailRenderer(repository).render(input, films)
            assertEquals(films.map { it.id }.toSet(), results.keys)
            for (film in films) {
                val output = results.getValue(film.id)
                assertNotSame(input, output)
                assertEquals(43, output.width); assertEquals(49, output.height)
                val expected = assets.open("render-fixtures/${film.id}-s1-e0-t0.rgba").use { it.readBytes() }
                for ((i, pixel) in pixels(output).withIndex()) {
                    assertEquals(255, pixel ushr 24)
                    for (channel in 0..2) {
                        val error = abs(((pixel ushr (16-8*channel)) and 255) - (expected[4*i+channel].toInt() and 255))
                        assertTrue("${film.id} pixel=$i channel=$channel error=$error", error <= 2)
                    }
                }
            }
            assertArrayEquals(original, pixels(input))
            assertFalse(input.isRecycled)
        } finally { results.values.forEach(Bitmap::recycle); input.recycle() }
    }

    // Missing downsampling or square stretching would waste memory or misrepresent the photo.
    @Test fun wideAndTallPhotosHaveBoundedAspectPreservingThumbnails() = runBlocking {
        val films = repository.listPresets()
        for ((width, height) in listOf(800 to 400, 400 to 800)) {
            val input = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff8b7050.toInt()) }
            var results = emptyMap<String, Bitmap>()
            try {
                results = PresetThumbnailRenderer(repository).render(input, films)
                assertEquals(3, results.size)
                for (output in results.values) {
                    assertEquals(if (width > height) 160 else 80, output.width)
                    assertEquals(if (height > width) 160 else 80, output.height)
                    assertNotSame(input, output)
                }
                assertEquals(0xff8b7050.toInt(), input.getPixel(0, 0))
                assertFalse(input.isRecycled)
            } finally { results.values.forEach(Bitmap::recycle); input.recycle() }
        }
    }
}
