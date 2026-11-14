package io.github.haaaappyday.filmeditor.render

import android.graphics.Bitmap
import io.github.haaaappyday.filmeditor.core.EditParams
import io.github.haaaappyday.filmeditor.core.FilmPreset
import io.github.haaaappyday.filmeditor.image.ImageSizing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Small, optional film-card previews; never used for the main preview or export. */
class PresetThumbnailRenderer(private val presets: AssetPresetRepository) {
    suspend fun render(input: Bitmap, films: List<FilmPreset>): Map<String, Bitmap> {
        val results = linkedMapOf<String, Bitmap>()
        try {
            return withContext(Dispatchers.Default) {
                require(films.size <= 3 && films.map { it.id }.distinct().size == films.size)
                val (width, height) = ImageSizing.fit(input.width, input.height, 160)
                val small = if (width == input.width && height == input.height) input
                    else Bitmap.createScaledBitmap(input, width, height, true)
                try {
                    val original = IntArray(width * height)
                    small.getPixels(original, 0, width, 0, 0, width, height)
                    for (film in films) {
                        val cube = presets.loadCube(film.id)
                        val params = EditParams(film.id)
                        val pixels = IntArray(original.size)
                        for (y in 0 until height) {
                            ensureActive()
                            for (x in 0 until width) {
                                val index = y * width + x
                                val value = original[index]
                                val rgb = ColorMath.transform(floatArrayOf(
                                    ((value ushr 16) and 255) / 255f,
                                    ((value ushr 8) and 255) / 255f,
                                    (value and 255) / 255f), cube, params)
                                pixels[index] = (255 shl 24) or
                                    ((rgb[0] * 255).roundToInt().coerceIn(0, 255) shl 16) or
                                    ((rgb[1] * 255).roundToInt().coerceIn(0, 255) shl 8) or
                                    (rgb[2] * 255).roundToInt().coerceIn(0, 255)
                            }
                        }
                        results[film.id] = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                    }
                    results
                } finally { if (small !== input) small.recycle() }
            }
        } catch (error: Throwable) {
            // Includes cancellation while dispatching the completed result back to Main.
            results.values.forEach(Bitmap::recycle)
            throw error
        }
    }
}
