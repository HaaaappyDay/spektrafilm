package io.github.haaaappyday.filmeditor.export

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.system.Os
import android.util.Base64
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
    private fun shell(command: String): String {
        // UiAutomation uses Runtime.exec, which does not interpret pipes or quoting.
        // Only generated fixture names/URIs/base64 are passed to this test-only script.
        val script = File(checkNotNull(context.getExternalFilesDir(null)), "export-fixture-${UUID.randomUUID()}.sh")
        return try {
            script.writeText("exec 2>&1\n$command\n")
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("sh ${script.absolutePath}")
            ).bufferedReader().use { it.readText() }
        } finally { script.delete() }
    }
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
    @Test fun failedPublishedRollbackBlocksRetryUntilThatInvocationIsCleaned() = runBlocking {
        for (cancelAfterPublish in listOf(false, true)) {
            val h = Harness()
            try {
                val source = h.source("orientation-1.jpg")
                val originalHash = hash(File(source.localPath))
                val request = ExportRequest(source, EditParams("kodak_portra_400"))
                val previous = h.exporter.export(request)
                val coordinator = ExportCoordinator(h.exporter, h.exporter::recover)
                h.gateway.refuseDelete = true
                if (cancelAfterPublish) {
                    lateinit var operation: Deferred<ExportResult>
                    h.gateway.afterPublish = { operation.cancel() }
                    operation = async(start = CoroutineStart.LAZY) { coordinator.export(request) }
                    operation.start()
                    try { operation.await(); fail("Cancellation returned success") } catch (_: CancellationException) { }
                } else {
                    h.gateway.afterPublish = { throw IOException("Provider committed publication then failed") }
                    try { coordinator.export(request); fail("Publication error returned success") }
                    catch (error: EditorException) { assertEquals(EditorErrorCode.EXPORT_FAILED, error.code) }
                }
                h.gateway.afterPublish = null
                val failedUri = h.gateway.created.last()
                assertTrue(h.gateway.rows().any { it.uri == failedUri && !it.pending })
                assertTrue(h.journal.read().isNotEmpty())
                val inserted = h.gateway.created.size
                try { coordinator.export(request); fail("Retry ignored unresolved published rollback") }
                catch (error: EditorException) { assertEquals(EditorErrorCode.EXPORT_FAILED, error.code) }
                assertEquals(inserted, h.gateway.created.size)
                assertTrue(h.journal.read().isNotEmpty())
                h.gateway.refuseDelete = false
                val retried = coordinator.export(request)
                assertEquals(setOf(previous.uri, retried.uri), h.gateway.rows().filter { it.uri in h.gateway.created }.map { it.uri }.toSet())
                assertFalse(h.gateway.rows().any { it.uri == failedUri })
                assertEquals(originalHash, hash(File(source.localPath)))
                h.assertNoUnpublishedOutput(); metadata(retried)
            } finally { h.close() }
        }
    }
    @Test fun freshExporterStillPreservesPublishedOutputFromAnInterruptedFailedInvocation() = runBlocking {
        val h = Harness()
        try {
            val request = ExportRequest(h.source("orientation-1.jpg"), EditParams("kodak_portra_400"))
            h.gateway.afterPublish = { throw IOException("Post-publication failure") }
            h.gateway.refuseDelete = true
            try { h.exporter.export(request); fail("Publication error returned success") }
            catch (error: EditorException) { assertEquals(EditorErrorCode.EXPORT_FAILED, error.code) }
            val published = h.gateway.created.single()
            h.gateway.afterPublish = null; h.gateway.refuseDelete = false
            val restarted = MediaStorePhotoExporter(context, h.images, h.renderer, h.journal, h.gateway, File(h.root, "staging"))
            restarted.recover()
            assertEquals(listOf(published), h.gateway.rows().filter { it.uri in h.gateway.created }.map { it.uri })
            assertFalse(h.gateway.rows().single { it.uri == published }.pending)
            assertTrue(h.journal.read().isEmpty())
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
            var uriWriteRejected = false
            val journal = PendingExportStore(context, File(h.root, "broken-journal"), commit = { pending, committed ->
                if (!JSONObject(pending.readText()).isNull("uri")) {
                    uriWriteRejected = true
                    throw IOException("URI journal write failed")
                }
                Os.rename(pending.absolutePath, committed.absolutePath)
            })
            val exporter = MediaStorePhotoExporter(context, h.images, h.renderer, journal, h.gateway, File(h.root, "staging"))
            try { exporter.export(request); fail("URI journal failure returned success") }
            catch (error: EditorException) { assertEquals(EditorErrorCode.EXPORT_FAILED, error.code) }
            assertTrue("URI journal failure was not injected", uriWriteRejected)
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
    @Suppress("DEPRECATION")
    @Test fun shellCreatedUnownedPhotosSurviveEvenWithForgedJournalUri() = runBlocking {
        val h = Harness(); val record = PendingExport.create()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val includingPending = MediaStore.setIncludePending(collection)
        val selection = "_display_name='${record.displayName}'"
        var foreign: Uri? = null
        try {
            val inserted = shell("content insert --uri $collection --bind _display_name:s:${record.displayName} --bind mime_type:s:image/jpeg --bind relative_path:s:${PendingExport.RELATIVE_PATH} --bind is_pending:i:1")
            // Insert intentionally prints nothing. Discover the row by its unique fixture name.
            val found = shell("content query --uri '$includingPending' --projection _id --where \"$selection\"")
            val ids = Regex("_id=(\\d+)").findAll(found).toList()
            assertEquals("insert=$inserted; query=$found", 1, ids.size)
            foreign = Uri.withAppendedPath(collection, ids.single().groupValues[1])
            val jpeg = instrumentation.context.assets.open("import-fixtures/orientation-1.jpg").use { it.readBytes() }
            val encoded = Base64.encodeToString(jpeg, Base64.NO_WRAP)
            shell("printf %s $encoded | base64 -d | content write --uri $foreign")
            val query = "content query --uri '${MediaStore.setIncludePending(foreign)}' --projection _id:owner_package_name:is_pending:_display_name"
            val before = shell(query)
            assertTrue(before, before.contains(record.displayName) && before.contains("is_pending=1"))
            // MediaStore assigns no app owner to shell-created media on these platforms.
            // This is an actual external row; never rewrite ownership to make a fake.
            assertTrue(before, before.contains("owner_package_name=NULL"))
            fun assertFixtureBytes() = assertArrayEquals(jpeg, Base64.decode(
                shell("content read --uri $foreign | base64"), Base64.DEFAULT))
            assertFixtureBytes()
            h.journal.write(record.copy(uri = foreign))
            h.exporter.recover()
            assertEquals(before, shell(query))
            assertFixtureBytes()
            shell("content update --uri $foreign --bind is_pending:i:0")
            h.journal.write(record.copy(uri = foreign))
            h.exporter.recover()
            val published = shell(query)
            assertTrue(published, published.contains(record.displayName) && published.contains("is_pending=0"))
            assertFixtureBytes()
            File(testOutputDirectory(), "export-external-owner-preservation.json").writeText(JSONObject()
                .put("pendingPreserved", true).put("publishedPreserved", true)
                .put("ownerMetadata", published.substringAfter("owner_package_name=").substringBefore(",")).toString(2))
        } finally {
            if (foreign != null) shell("content delete --uri '${MediaStore.setIncludePending(foreign)}'")
            else shell("content delete --uri '$includingPending' --where \"$selection\"")
            h.close()
        }
    }
}
