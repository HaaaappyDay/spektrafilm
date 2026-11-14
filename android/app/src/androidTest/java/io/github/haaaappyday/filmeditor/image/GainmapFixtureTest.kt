package io.github.haaaappyday.filmeditor.image

import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import io.github.haaaappyday.filmeditor.testsupport.testOutputDirectory

class GainmapFixtureTest {
    @Test fun nativeGainmapJpegUsesItsKnownSdrBase() {
        if (Build.VERSION.SDK_INT >= 34) generateAndCheck()
    }

    @Test fun archivedGainmapHasTheSameSdrPixelsOnOldAndNewAndroid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "archived-gainmap.jpg")
        instrumentation.context.assets.open("import-fixtures/synthetic-gainmap.jpg").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        checkSdrBase(file)
    }

    private fun checkSdrBase(file: File) {
        val normalizer = ImageNormalizer()
        val metadata = normalizer.inspect(file)
        assertEquals(32, metadata.width); assertEquals(24, metadata.height)
        val bitmap = normalizer.decode(file, 1600)
        try {
            assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
            assertEquals(ColorSpace.get(ColorSpace.Named.SRGB), bitmap.colorSpace)
            if (Build.VERSION.SDK_INT >= 34) assertFalse(bitmap.hasGainmap())
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                assertTrue(kotlin.math.abs(Color.red(pixel) - 100) <= 2)
                assertTrue(kotlin.math.abs(Color.green(pixel) - 140) <= 2)
                assertTrue(kotlin.math.abs(Color.blue(pixel) - 170) <= 2)
                assertEquals(255, Color.alpha(pixel))
            }
        } finally { bitmap.recycle() }
    }

    @RequiresApi(34)
    private fun generateAndCheck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = testOutputDirectory()
        val file = File(directory, "synthetic-gainmap.jpg")
        val base = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(100, 140, 170)) }
        val gain = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        try {
            base.gainmap = Gainmap(gain).apply { setRatioMax(2f, 2f, 2f); setDisplayRatioForFullHdr(2f) }
            file.outputStream().use { assertTrue(base.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            val raw = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file))
            try { assertTrue("Fixture must contain a real gain map", raw.hasGainmap()) } finally { raw.recycle() }
            checkSdrBase(file)
        } finally { base.recycle(); gain.recycle() }
    }
}
