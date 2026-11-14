package io.github.haaaappyday.filmeditor.ui.editor

import android.system.Os
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class EditorSessionStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun root() = File(context.cacheDir, "session-${UUID.randomUUID()}").apply { mkdirs() }
    private fun source(directory: File): ImageSource {
        directory.mkdirs(); val id = UUID.randomUUID().toString()
        val file = File(directory, "$id.source").apply { writeText("test bytes") }
        return ImageSource(id, file.absolutePath, 4000, 3000, "image/jpeg")
    }
    @Test fun roundtripAndInterruptedWriteKeepLastAtomicSnapshot() = runBlocking {
        val root = root(); val files = File(root, "sources"); val state = File(root, "state")
        try {
            val store = EditorSessionStore(context, state, files)
            val edit = StoredEdit(source(files), EditParams("kodak_gold_200", .37f, -.7f, 31f))
            store.save(edit); assertEquals(edit, store.load())
            val atomic = AtomicFile(File(state, "editor.json"))
            atomic.startWrite().use { it.write("{interrupted".toByteArray()) } // process died before finishWrite
            assertEquals(edit, EditorSessionStore(context, state, files).load())
            assertEquals("test bytes", File(edit.source.localPath).readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun startupPrunesOnlyManagedOrphansAndKeepsDurableSource() = runBlocking {
        val root = root(); val files = File(root, "sources"); val state = File(root, "state")
        try {
            val keep = source(files); val orphan = source(files)
            val foreign = File(files, "other.source").apply { writeText("untouched") }
            val external = File(root, "external").apply { writeText("private") }
            val link = File(files, "${UUID.randomUUID()}.source")
            Os.symlink(external.absolutePath, link.absolutePath)
            val store = EditorSessionStore(context, state, files)
            store.save(StoredEdit(keep, EditParams("kodak_portra_400")))
            store.load()
            assertTrue(File(keep.localPath).exists()); assertFalse(File(orphan.localPath).exists())
            assertTrue(foreign.exists()); assertEquals("private", external.readText()); assertTrue(link.exists())
            // A second ViewModel in this same process must not prune a live candidate.
            val candidate = source(files)
            EditorSessionStore(context, state, files).load()
            assertTrue(File(candidate.localPath).exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun corruptNonfiniteAndForgedPathsFailAsReadableRecoveryErrors() = runBlocking {
        val root = root(); val files = File(root, "sources"); val state = File(root, "state")
        try {
            val store = EditorSessionStore(context, state, files)
            val edit = StoredEdit(source(files), EditParams("kodak_portra_400"))
            store.save(edit)
            val file = File(state, "editor.json"); val valid = file.readText()
            val badFloat = JSONObject(valid).apply { getJSONObject("params").put("strength", "NaN") }.toString()
            val badPath = JSONObject(valid).apply { getJSONObject("source").put("localPath", File(root, "external").absolutePath) }.toString()
            for (invalid in listOf("{broken", badFloat, badPath, "x".repeat(65537))) {
                file.writeText(invalid)
                try { store.load(); fail("Accepted corrupt session") }
                catch (error: EditorException) { assertEquals(EditorErrorCode.READ_FAILED, error.code) }
            }
        } finally { root.deleteRecursively() }
    }
}
