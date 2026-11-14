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
    @Test fun newerAndroidRejectsARealSyntheticGainmapJpeg() {
        if (Build.VERSION.SDK_INT >= 34) generateAndCheck()
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
            val normalizer = ImageNormalizer()
            for (inspect in listOf(true, false)) {
                val error = assertThrows(EditorException::class.java) {
                    if (inspect) normalizer.inspect(file) else normalizer.decode(file, 1600)
                }
                assertEquals(EditorErrorCode.UNSUPPORTED_IMAGE, error.code)
            }
        } finally { base.recycle(); gain.recycle() }
    }
}
