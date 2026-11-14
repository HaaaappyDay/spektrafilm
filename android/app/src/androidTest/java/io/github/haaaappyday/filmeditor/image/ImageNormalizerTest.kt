package io.github.haaaappyday.filmeditor.image

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.Rule
import org.junit.rules.TestName
import io.github.haaaappyday.filmeditor.testsupport.testOutputDirectory
import java.io.File

class ImageNormalizerTest {
    @get:Rule val testName = TestName()
    private var maxError = 0
    @After fun recordSampledColorError() {
        File(testOutputDirectory(), "normalization-${testName.methodName}.json")
            .writeText(JSONObject().put("maxSampledRgbCodeError", maxError).toString())
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val normalizer = ImageNormalizer()
    private fun fixture(name: String): File = File(instrumentation.targetContext.cacheDir, "fixture-$name").also { file ->
        instrumentation.context.assets.open("import-fixtures/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
    }
    private fun expected() = JSONObject(instrumentation.context.assets.open("import-fixtures/expected.json").bufferedReader().use { it.readText() })
    private fun normalized(bitmap: Bitmap) {
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
        assertEquals(ColorSpace.get(ColorSpace.Named.SRGB), bitmap.colorSpace)
        assertFalse(bitmap.hasAlpha())
    }
    private fun assertColor(expected: IntArray, actual: Int, tolerance: Int = 2) {
        val channels = intArrayOf(Color.red(actual), Color.green(actual), Color.blue(actual))
        for (i in 0..2) maxError = maxOf(maxError, kotlin.math.abs(expected[i] - channels[i]))
        for (i in 0..2) assertTrue("RGB expected=${expected.toList()}, actual=${channels.toList()}", kotlin.math.abs(expected[i] - channels[i]) <= tolerance)
        assertEquals(255, Color.alpha(actual))
    }

    @Test fun allEightExifOrientationsAreAppliedExactlyOnceAtBothCaps() {
        val cases = expected().getJSONArray("orientations")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val file = fixture(case.getString("file"))
            val metadata = normalizer.inspect(file)
            assertEquals(case.getInt("width"), metadata.width)
            assertEquals(case.getInt("height"), metadata.height)
            assertEquals("image/jpeg", metadata.mimeType)
            for (cap in listOf(40, 4096)) {
                val bitmap = normalizer.decode(file, cap)
                try {
                    normalized(bitmap)
                    val (w, h) = ImageSizing.fit(metadata.width, metadata.height, cap)
                    assertEquals(w, bitmap.width); assertEquals(h, bitmap.height)
                    val corners = case.getJSONArray("corners")
                    for (corner in 0..3) {
                        val color = corners.getJSONArray(corner)
                        assertColor(IntArray(3) { color.getInt(it) }, bitmap.getPixel(
                            if (corner % 2 == 0) w / 4 else 3 * w / 4,
                            if (corner < 2) h / 4 else 3 * h / 4))
                    }
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun taggedColorsConvertRatherThanRelabelAndUntaggedDefaultsToSrgb() {
        for (name in listOf("untagged.png", "srgb.png", "untagged.jpg", "srgb.jpg", "display-p3.png")) {
            val bitmap = normalizer.decode(fixture(name), 1600)
            try {
                normalized(bitmap)
                val patches = expected().getJSONArray(if (name == "display-p3.png") "p3Expected" else "srgbExpected")
                for (i in 0..2) assertColor(IntArray(3) { patches.getJSONArray(i).getInt(it) }, bitmap.getPixel(20 + 40*i, 20))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun transparencyCompositesOntoWhiteInEncodedSrgb() {
        val bitmap = normalizer.decode(fixture("transparent.png"), 1600)
        try {
            normalized(bitmap)
            val expected = listOf(intArrayOf(255,127,127), intArrayOf(255,255,255), intArrayOf(0,0,255), intArrayOf(201,211,221))
            expected.forEachIndexed { i, color -> assertColor(color, bitmap.getPixel(i, 0), 1) }
        } finally { bitmap.recycle() }
    }

    @Test fun realLargeSourcesAreSampledAndSmallSourcesAreNotUpscaled() {
        for ((name, cap, size) in listOf(
            Triple("12mp.jpg", 1600, 1600 to 1200), Triple("12mp.jpg", 4096, 4000 to 3000),
            Triple("24mp.jpg", 4096, 4096 to 2731), Triple("48mp.jpg", 1600, 1600 to 1200),
            Triple("tiny.png", 1600, 1 to 1), Triple("extreme.png", 1600, 1 to 1600))) {
            val bitmap = normalizer.decode(fixture(name), cap)
            try {
                normalized(bitmap)
                assertEquals(size.first, bitmap.width); assertEquals(size.second, bitmap.height)
            } finally { bitmap.recycle() }
        }
    }

    @Test fun unsupportedAnimatedAndCorruptInputsNeverProducePartialSuccess() {
        for (name in listOf("unsupported.webp", "animated.png", "corrupt.jpg", "truncated.jpg", "truncated-pixels.jpg", "truncated-pixels.png")) {
            val error = assertThrows(EditorException::class.java) { normalizer.inspect(fixture(name)) }
            assertTrue(error.code in setOf(EditorErrorCode.UNSUPPORTED_IMAGE, EditorErrorCode.READ_FAILED))
        }
    }
}
