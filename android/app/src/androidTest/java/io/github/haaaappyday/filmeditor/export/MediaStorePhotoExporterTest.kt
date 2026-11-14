package io.github.haaaappyday.filmeditor.export

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.net.Uri
import android.provider.MediaStore
import android.system.Os
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.core.*
import io.github.haaaappyday.filmeditor.image.AndroidImageRepository
import io.github.haaaappyday.filmeditor.render.AssetPresetRepository
import io.github.haaaappyday.filmeditor.render.GlFilmRenderer
import io.github.haaaappyday.filmeditor.testsupport.testOutputDirectory
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.security.MessageDigest
import java.util.UUID

class MediaStorePhotoExporterTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private enum class Fault { NONE, INSERT, OPEN, WRITE, CLOSE, PUBLISH, DELETE }
    private inner class Gateway : MediaStoreGateway {
        private val real = AndroidMediaStoreGateway(context)
        val created = mutableListOf<Uri>()
        var fault = Fault.NONE
        var afterPublish: (() -> Unit)? = null
        var afterWrite: (() -> Unit)? = null
        var refuseDelete = false
        override fun insert(name: String): Uri {
            if (fault == Fault.INSERT) throw IOException("insert failed")
            return real.insert(name).also { created.add(it) }
        }
        override fun open(uri: Uri): OutputStream {
            if (fault == Fault.OPEN) throw IOException("open failed")
            return object : FilterOutputStream(real.open(uri)) {
                override fun write(bytes: ByteArray, offset: Int, count: Int) {
                    if (fault == Fault.WRITE) throw IOException("ENOSPC: no space left")
                    out.write(bytes, offset, count); afterWrite?.invoke()
                }
                override fun close() {
                    super.close()
                    if (fault == Fault.CLOSE) throw IOException("close failed")
                }
            }
        }
        override fun publish(uri: Uri, name: String) {
            if (fault == Fault.PUBLISH) throw IOException("publish failed")
            real.publish(uri, name); afterPublish?.invoke()
        }
        override fun rows() = real.rows()
        override fun delete(row: MediaRow, onlyPending: Boolean) {
            if (fault == Fault.DELETE || refuseDelete) throw IOException("delete failed")
            real.delete(row, onlyPending)
        }
        @Suppress("DEPRECATION")
        fun cleanTestRows() {
            fault = Fault.NONE; refuseDelete = false
            for (row in rows().filter { it.uri in created }) {
                if (PendingExport.isManagedName(row.name)) real.delete(row, false)
                else resolver.delete(MediaStore.setIncludePending(row.uri), null, null)
            }
        }
    }
    private inner class Harness {
        val root = File(context.cacheDir, "export-test-${UUID.randomUUID()}").canonicalFile.apply { mkdirs() }
        val gateway = Gateway()
        val journal = PendingExportStore(context, File(root, "journal"))
        val repository = AndroidImageRepository(context, directory = { File(root, "sources") }, openInput = { uri ->
            if (uri.lastPathSegment == "gps.jpg") File(root, "gps.jpg").inputStream()
            else instrumentation.context.assets.open("import-fixtures/${uri.lastPathSegment}")
        })
        val decoded = mutableListOf<Bitmap>()
        val images = object : ImageRepository by repository {
            override suspend fun decode(source: ImageSource, maxLongEdge: Int): Bitmap = repository.decode(source, maxLongEdge).also { decoded.add(it) }
        }
        val renderer = GlFilmRenderer(context, AssetPresetRepository(context))
        val encoded = mutableListOf<Bitmap>()
        var encoderFailure = false
        var inspect: ((Bitmap) -> Unit)? = null
        val exporter = MediaStorePhotoExporter(context, images, renderer, journal, gateway, File(root, "staging")) { bitmap, file ->
            encoded.add(bitmap); inspect?.invoke(bitmap)
            if (encoderFailure) throw IOException("encoder failed")
            encodeSrgbJpeg(bitmap, file)
        }
        suspend fun source(name: String) = images.importImage(Uri.parse("content://export-fixture/$name"))
        suspend fun close() { renderer.release(); gateway.cleanTestRows(); root.deleteRecursively() }
        fun assertNoUnpublishedOutput() {
            assertTrue(gateway.rows().none { it.uri in gateway.created && it.pending })
            assertTrue(File(root, "staging").listFiles().orEmpty().isEmpty())
            assertTrue(journal.read().isEmpty())
        }
    }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun metadata(result: ExportResult) {
        resolver.openInputStream(result.uri)!!.use { input ->
            val exif = ExifInterface(input)
            assertEquals(1, exif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, -1))
            assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        }
        val bitmap = resolver.openInputStream(result.uri)!!.use { BitmapFactory.decodeStream(it) }!!
        try {
            assertEquals(result.width, bitmap.width); assertEquals(result.height, bitmap.height)
            assertEquals(ColorSpace.get(ColorSpace.Named.SRGB), bitmap.colorSpace)
        } finally { bitmap.recycle() }
    }
    @Test fun realOutputSizesMetadataAndOriginalHashes() = runBlocking {
        val h = Harness(); val evidence = JSONArray()
        try {
            val original = File(h.root, "gps.jpg")
            instrumentation.context.assets.open("import-fixtures/orientation-6.jpg").use { input -> original.outputStream().use { input.copyTo(it) } }
            ExifInterface(original).apply { setLatLong(31.2, 121.5); saveAttributes() }
            val originalHash = hash(original)
            for ((fixture, dimensions) in listOf("12mp.jpg" to (4000 to 3000), "24mp.jpg" to (4096 to 2731), "tiny.png" to (1 to 1), "gps.jpg" to (60 to 80))) {
                val source = h.source(fixture); val before = hash(File(source.localPath))
                val result = h.exporter.export(ExportRequest(source, EditParams("kodak_portra_400", .37f, .3f, 24f)))
                assertEquals(dimensions.first, result.width); assertEquals(dimensions.second, result.height)
                metadata(result); assertEquals(before, hash(File(source.localPath)))
                assertFalse(h.gateway.rows().single { it.uri == result.uri }.pending)
                h.assertNoUnpublishedOutput()
                evidence.put(JSONObject().put("fixture", fixture).put("width", result.width).put("height", result.height).put("sourceSha256", before).put("sourceUnchanged", true))
            }
            assertEquals(originalHash, hash(original)); assertTrue(h.encoded.all { it.isRecycled }); assertTrue(h.decoded.all { it.isRecycled })
            File(testOutputDirectory(), "export-dimensions-metadata-hashes.json").writeText(evidence.toString(2))
        } finally { h.close() }
    }
    @Test fun allOrientationsAndAlphaMatchSameSizePreviewBeforeJpegEncoding() = runBlocking {
        val h = Harness(); val evidence = JSONArray()
        try {
            val params = EditParams("kodak_gold_200", .37f, -.7f, 31f)
            for (fixture in (1..8).map { "orientation-$it.jpg" } + "transparent.png") {
                val source = h.source(fixture)
                val input = h.images.decode(source, 1600)
                val expected = try { h.renderer.render(input, params) } finally { input.recycle() }
                var maxError = 0
                h.inspect = { actual ->
                    assertEquals(expected.width, actual.width); assertEquals(expected.height, actual.height)
                    for (y in 0 until actual.height) for (x in 0 until actual.width) {
                        val a = actual.getPixel(x, y); val b = expected.getPixel(x, y)
                        for (shift in listOf(0, 8, 16)) maxError = maxOf(maxError, kotlin.math.abs(((a shr shift) and 255) - ((b shr shift) and 255)))
                    }
                    assertTrue("$fixture pre-JPEG mismatch: $maxError", maxError <= 2)
                }
                try {
                    val result = h.exporter.export(ExportRequest(source, params)); metadata(result)
                    evidence.put(JSONObject().put("fixture", fixture).put("maxPreEncodingRgbCodeError", maxError))
                } finally { expected.recycle(); h.inspect = null }
            }
            File(testOutputDirectory(), "export-orientation-alpha-color.json").writeText(evidence.toString(2))
        } finally { h.close() }
    }
    @Test fun everyWriteFailureAndEncoderFailureLeaveNoPendingOutputAndRetryOnce() = runBlocking {
        val h = Harness(); val evidence = JSONArray()
        try {
            val source = h.source("orientation-1.jpg"); val original = hash(File(source.localPath))
            val request = ExportRequest(source, EditParams("kodak_portra_400"))
            for (fault in listOf(Fault.INSERT, Fault.OPEN, Fault.WRITE, Fault.CLOSE, Fault.PUBLISH)) {
                h.gateway.fault = fault
                try { h.exporter.export(request); fail("False success for $fault") }
                catch (error: EditorException) { assertEquals(EditorErrorCode.EXPORT_FAILED, error.code) }
                h.gateway.fault = Fault.NONE
                h.assertNoUnpublishedOutput()
                assertEquals(original, hash(File(source.localPath)))
                evidence.put(JSONObject().put("fault", fault.name).put("cleaned", true))
            }
            h.encoderFailure = true
            try { h.exporter.export(request); fail("Encoder falsely succeeded") }
            catch (error: EditorException) { assertEquals(EditorErrorCode.EXPORT_FAILED, error.code) }
            h.encoderFailure = false; h.assertNoUnpublishedOutput()
            val result = h.exporter.export(request); metadata(result)
            assertEquals(1, h.gateway.rows().count { it.uri in h.gateway.created && !it.pending })
            assertTrue(h.encoded.all { it.isRecycled }); assertTrue(h.decoded.all { it.isRecycled })
            File(testOutputDirectory(), "export-failure-cleanup.json").writeText(evidence.toString(2))
        } finally { h.close() }
    }
    @Test fun cancellationDuringWriteAndAfterPublicationRollBackOnlyThisOperation() = runBlocking {
        val h = Harness()
        try {
            val request = ExportRequest(h.source("orientation-1.jpg"), EditParams("kodak_portra_400"))
            val preserved = h.exporter.export(request)
            for (phase in listOf("write", "publish")) {
                lateinit var job: Deferred<ExportResult>
                if (phase == "write") h.gateway.afterWrite = { job.cancel() }
                else h.gateway.afterPublish = { job.cancel() }
                job = async(start = CoroutineStart.LAZY) { h.exporter.export(request) }
                job.start()
                try { job.await(); fail("Cancelled export returned success: $phase") } catch (_: CancellationException) { }
                h.gateway.afterWrite = null; h.gateway.afterPublish = null
                h.assertNoUnpublishedOutput()
                assertEquals(listOf(preserved.uri), h.gateway.rows().filter { it.uri in h.gateway.created }.map { it.uri })
            }
            metadata(preserved)
        } finally { h.close() }
    }
    @Test fun restartRecoversJournalAndInsertWindowButPreservesPublishedAndOtherMedia() = runBlocking {
        val h = Harness()
        try {
            val published = h.exporter.export(ExportRequest(h.source("orientation-1.jpg"), EditParams("kodak_portra_400")))
            val journaled = PendingExport.create(); val uri = h.gateway.insert(journaled.displayName)
            h.journal.write(journaled.copy(uri = uri))
            val noUri = PendingExport.create(); h.journal.write(noUri); val window = h.gateway.insert(noUri.displayName)
            val other = h.gateway.insert("unrelated-${UUID.randomUUID()}.jpg")
            h.gateway.fault = Fault.DELETE
            try { h.exporter.recover(); fail("Recovery falsely succeeded") }
            catch (error: EditorException) { assertEquals(EditorErrorCode.EXPORT_FAILED, error.code) }
            assertTrue(h.journal.read().isNotEmpty())
            h.gateway.fault = Fault.NONE
            h.exporter.recover()
            val remaining = h.gateway.rows().map { it.uri }
            assertFalse(uri in remaining); assertFalse(window in remaining)
            assertTrue(published.uri in remaining); assertTrue(other in remaining)
            assertTrue(h.journal.read().isEmpty()); metadata(published)
        } finally { h.close() }
    }
    @Test fun uriJournalFailureAndFailedRollbackRemainRecoverable() = runBlocking {
        val h = Harness()
        try {
            val request = ExportRequest(h.source("orientation-1.jpg"), EditParams("kodak_portra_400"))
            val journal = PendingExportStore(context, File(h.root, "broken-journal"), commit = { pending, committed ->
                if (pending.readText().contains("content://")) throw IOException("URI journal write failed")
                Os.rename(pending.absolutePath, committed.absolutePath)
            })
            val exporter = MediaStorePhotoExporter(context, h.images, h.renderer, journal, h.gateway, File(h.root, "staging"))
            try { exporter.export(request); fail("URI journal failure returned success") }
            catch (error: EditorException) { assertEquals(EditorErrorCode.EXPORT_FAILED, error.code) }
            assertTrue(journal.read().isEmpty()); h.assertNoUnpublishedOutput()
            h.gateway.fault = Fault.WRITE; h.gateway.refuseDelete = true
            try { h.exporter.export(request); fail("Write failure returned success") }
            catch (error: EditorException) { assertEquals(EditorErrorCode.EXPORT_FAILED, error.code) }
            assertTrue(h.journal.read().isNotEmpty())
            assertTrue(h.gateway.rows().any { it.uri in h.gateway.created && it.pending })
            h.gateway.fault = Fault.NONE; h.gateway.refuseDelete = false
            h.exporter.recover(); h.assertNoUnpublishedOutput()
        } finally { h.close() }
    }
    @Suppress("DEPRECATION")
    @Test fun corruptJournalAndForeignIdentityCannotDeleteProtectedMedia() = runBlocking {
        val h = Harness()
        val otherRecord = PendingExport.create()
        val outside = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, otherRecord.displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OtherExportTest/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        try {
            h.journal.write(otherRecord.copy(uri = outside))
            val protected = h.gateway.insert(PendingExport.create().displayName)
            val orphan = h.gateway.insert(PendingExport.create().displayName)
            File(h.root, "journal/${java.util.UUID.randomUUID()}.json").writeText("{corrupt")
            val gateway = object : MediaStoreGateway by h.gateway {
                override fun rows() = h.gateway.rows().map { if (it.uri == protected) it.copy(owner = "foreign.app") else it }
            }
            val exporter = MediaStorePhotoExporter(context, h.images, h.renderer, h.journal, gateway, File(h.root, "staging"))
            exporter.recover()
            assertTrue(h.gateway.rows().any { it.uri == protected })
            assertFalse(h.gateway.rows().any { it.uri == orphan })
            resolver.query(MediaStore.setIncludePending(outside), arrayOf(MediaStore.Images.Media._ID), null, null, null)!!.use { assertTrue(it.moveToFirst()) }
            assertTrue(h.journal.read().isEmpty())
        } finally {
            resolver.delete(MediaStore.setIncludePending(outside), null, null)
            h.close()
        }
    }
    @Test fun tenRepeatedExportsReleaseTemporaryFilesAndDescriptors() = runBlocking {
        val h = Harness()
        try {
            val request = ExportRequest(h.source("orientation-1.jpg"), EditParams("kodak_ektar_100"))
            val counts = mutableListOf<Int>(); val results = mutableSetOf<Uri>()
            repeat(10) {
                results.add(h.exporter.export(request).uri); h.assertNoUnpublishedOutput()
                counts.add(File("/proc/self/fd").list()!!.size)
            }
            assertEquals(10, results.size); assertTrue(h.encoded.all { it.isRecycled }); assertTrue(h.decoded.all { it.isRecycled })
            assertTrue("Descriptors continuously grew: $counts", counts.last() <= counts[2] + 4)
            File(testOutputDirectory(), "export-repeat-resources.json").writeText(JSONObject().put("exports", 10).put("uniqueUris", results.size).put("openFdCounts", JSONArray(counts)).put("encodedBitmapsRecycled", true).toString(2))
        } finally { h.close() }
    }
}
