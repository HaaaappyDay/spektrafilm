package io.github.haaaappyday.filmeditor.image

import android.net.Uri
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AndroidImageRepositoryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun root() = File(context.noBackupFilesDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
    private fun bytes(name: String = "orientation-1.jpg") = instrumentation.context.assets.open("import-fixtures/$name").use { it.readBytes() }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toList()

    @Test fun realContentUriCreatesDurableUnmodifiedCopyDespiteMisleadingProviderMime() = runBlocking {
        val directory = root()
        val repo = AndroidImageRepository(context, directory = { directory })
        val uri = Uri.parse("content://io.github.haaaappyday.filmeditor.test.fixtures/orientation-3.jpg")
        val original = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val source = repo.importImage(uri)
        try {
            assertEquals("image/jpeg", source.mimeType)
            assertEquals(hash(original), hash(File(source.localPath).readBytes()))
            assertEquals(hash(original), hash(context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }))
            context.contentResolver.delete(uri, null, null)
            val bitmap = repo.decode(source, 1600)
            try { assertEquals(80, bitmap.width); assertEquals(60, bitmap.height) } finally { bitmap.recycle() }
            repo.remove(source)
            assertFalse(File(source.localPath).exists())
        } finally { directory.deleteRecursively() }
    }

    @Test fun failuresAndCancellationLeaveNoNewFilesAndPreserveOldSources() = runBlocking {
        val directory = root()
        val uri = Uri.parse("content://fixture/any.jpg")
        val good = AndroidImageRepository(context, directory = { directory }, openInput = { ByteArrayInputStream(bytes()) })
        val source = good.importImage(uri)
        try {
            for (data in listOf(bytes("unsupported.webp"), bytes("corrupt.jpg"), bytes("truncated.jpg"), bytes("truncated-pixels.jpg"), bytes("truncated-pixels.png"), bytes("animated.png"))) {
                val bad = AndroidImageRepository(context, directory = { directory }, openInput = { ByteArrayInputStream(data) })
                try { bad.importImage(uri); fail("Expected rejection") } catch (_: EditorException) { }
            }
            val denied = AndroidImageRepository(context, directory = { directory }, openInput = { throw SecurityException("revoked") })
            try { denied.importImage(uri); fail("Expected read failure") } catch (error: EditorException) { assertEquals(EditorErrorCode.READ_FAILED, error.code) }
            val fault = AndroidImageRepository(context, directory = { directory }, openInput = {
                object : InputStream() { override fun read(): Int = throw IOException("no space / stream failure") }
            })
            try { fault.importImage(uri); fail("Expected I/O failure") } catch (error: EditorException) { assertEquals(EditorErrorCode.READ_FAILED, error.code) }
            val parent = Job()
            val cancelled = AndroidImageRepository(context, directory = { directory }, openInput = {
                object : ByteArrayInputStream(bytes()) {
                    override fun read(b: ByteArray, off: Int, len: Int): Int { parent.cancel(); return super.read(b, off, len) }
                }
            })
            try { withContext(parent) { cancelled.importImage(uri) }; fail("Expected cancellation") } catch (_: CancellationException) { }
            assertEquals(listOf(File(source.localPath).name), directory.listFiles()!!.map { it.name })
            assertEquals(hash(bytes()), hash(File(source.localPath).readBytes()))
        } finally { directory.deleteRecursively() }
    }

    @Test fun byteLimitRejectsStreamingInputWithoutBufferingTheWholeSource() = runBlocking {
        val directory = root()
        val repo = AndroidImageRepository(context, directory = { directory }, openInput = {
            object : InputStream() {
                override fun read() = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    java.util.Arrays.fill(b, off, off + len, 0.toByte())
                    return len
                }
            }
        })
        try {
            try { repo.importImage(Uri.EMPTY); fail("Expected byte limit") }
            catch (error: EditorException) { assertEquals(EditorErrorCode.IMAGE_TOO_LARGE, error.code) }
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun stalePartCleanupDoesNotDeleteActiveImportsOrCompletedSources() = runBlocking {
        val directory = root()
        val stale = File(directory, "${UUID.randomUUID()}.part").apply { writeText("abandoned") }
        val retained = File(directory, "${UUID.randomUUID()}.source").apply { writeText("retained") }
        val entered = CountDownLatch(1); val resume = CountDownLatch(1)
        val first = AndroidImageRepository(context, directory = { directory }, openInput = {
            object : ByteArrayInputStream(bytes()) {
                var waited = false
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (!waited) { waited = true; entered.countDown(); check(resume.await(10, TimeUnit.SECONDS)) }
                    return super.read(b, off, len)
                }
            }
        })
        val import = async(Dispatchers.Default) { first.importImage(Uri.EMPTY) }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val activePart = directory.listFiles()!!.single { it.extension == "part" }
            val second = AndroidImageRepository(context, directory = { directory }, openInput = { ByteArrayInputStream(bytes()) })
            val another = second.importImage(Uri.EMPTY)
            assertFalse(stale.exists()); assertTrue(retained.exists()); assertTrue(activePart.exists())
            resume.countDown()
            assertTrue(File(import.await().localPath).exists())
            second.remove(another)
        } finally { resume.countDown(); import.cancelAndJoin(); directory.deleteRecursively() }
    }

    @Test fun forgedDescriptorsAndSymlinksCannotAccessOrRemoveExternalFiles() = runBlocking {
        val directory = root()
        val external = File(context.cacheDir, "outside-${UUID.randomUUID()}.jpg").apply { writeBytes(bytes()) }
        val repo = AndroidImageRepository(context, directory = { directory })
        val id = UUID.randomUUID().toString()
        val symlink = File(directory, "$id.source")
        Os.symlink(external.path, symlink.path)
        try {
            for (path in listOf(external.path, symlink.path, File(directory, "../${external.name}").path)) {
                val forged = ImageSource(id, path, 80, 60, "image/jpeg")
                try { repo.decode(forged, 1600); fail("Expected invalid path") } catch (_: EditorException) { }
                try { repo.remove(forged); fail("Expected invalid path") } catch (_: EditorException) { }
                assertEquals(hash(bytes()), hash(external.readBytes()))
            }
        } finally { symlink.delete(); external.delete(); directory.deleteRecursively() }
    }
}
