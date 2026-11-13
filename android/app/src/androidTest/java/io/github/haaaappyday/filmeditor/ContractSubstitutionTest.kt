package io.github.haaaappyday.filmeditor

import android.graphics.Bitmap
import android.net.Uri
import io.github.haaaappyday.filmeditor.core.*
import io.github.haaaappyday.filmeditor.fakes.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ContractSubstitutionTest {
    @Test fun servicesAreReplaceableWithoutChangingSharedModels() = runBlocking {
        val source = ImageSource("source", "/private/source.png", 2, 1, "image/png")
        val preset = FilmPreset("portra", "Portra", "presets/portra.cube", "checksum")
        val uri = Uri.parse("content://test/source")
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        var removed = false
        val images: ImageRepository = FakeImageRepository(
            onImport = { assertEquals(uri, it); source },
            onDecode = { input, cap ->
                assertEquals(source, input); assertEquals(1600, cap)
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            },
            onRemove = { assertEquals(source, it); removed = true },
        )
        val presets: PresetRepository = FakePresetRepository(listOf(preset))
        val fakeRenderer = FakeFilmRenderer { input, params ->
            assertEquals("portra", params.presetId)
            input.copy(Bitmap.Config.ARGB_8888, false)
        }
        val renderer: FilmRenderer = fakeRenderer
        val exporter: PhotoExporter = FakePhotoExporter {
            assertEquals(source, it.source)
            ExportResult(uri, source.width, source.height)
        }
        val decoded = images.decode(images.importImage(uri), 1600)
        val params = EditParams(presets.listPresets().single().id)
        val rendered = renderer.render(decoded, params)
        try {
            assertNotSame(decoded, rendered)
            assertEquals(2, rendered.width)
            assertEquals(255, android.graphics.Color.alpha(rendered.getPixel(0, 0)))
            assertEquals(ExportResult(uri, 2, 1), exporter.export(ExportRequest(source, params)))
            images.remove(source)
            assertTrue(removed)
            renderer.release()
            assertEquals(1, fakeRenderer.releaseCalls)
        } finally {
            bitmap.recycle(); decoded.recycle(); rendered.recycle()
        }
    }

    @Test fun cancellationCanPropagateThroughTheSuspendContract() = runBlocking {
        val cancelled = CancellationException("test cancellation")
        val exporter: PhotoExporter = FakePhotoExporter { throw cancelled }
        try {
            exporter.export(ExportRequest(ImageSource("id", "/private/a", 1, 1, "image/png"), EditParams("p")))
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }
}
