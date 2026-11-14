package io.github.haaaappyday.filmeditor.export

import android.net.Uri
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PendingExportStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun intentUriUpdateInterruptedWriteAndFailedCommitKeepLastSnapshot() {
        val root = File(context.cacheDir, "export-journal-${UUID.randomUUID()}").canonicalFile
        try {
            var refuse = false
            val store = PendingExportStore(context, root, commit = { pending, committed ->
                if (!refuse) Os.rename(pending.absolutePath, committed.absolutePath)
            })
            val intent = PendingExport.create()
            store.write(intent); assertEquals(listOf(intent), store.read())
            val attached = intent.copy(uri = Uri.parse("content://media/external_primary/images/media/123"))
            refuse = true
            try { store.write(attached); fail("Accepted failed commit") } catch (_: java.io.IOException) { }
            assertEquals(listOf(intent), store.read())
            refuse = false; store.write(attached)
            File(root, "${intent.id}.json.new").writeText("{interrupted")
            assertEquals(listOf(attached), store.read())
            store.remove(intent.id); assertTrue(store.read().isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun corruptAndForgedRecordsCannotAuthorizeDeletionAndForeignFilesSurvive() {
        val root = File(context.cacheDir, "export-journal-${UUID.randomUUID()}").canonicalFile.apply { mkdirs() }
        try {
            val store = PendingExportStore(context, root)
            val record = PendingExport.create(); store.write(record)
            val file = File(root, "${record.id}.json"); val valid = file.readText()
            for (invalid in listOf("{broken", "x".repeat(65537), valid.replace("null", "\"content://foreign/private/12\""))) {
                file.writeText(invalid); assertTrue(store.read().isEmpty())
            }
            val foreign = File(root, "notes.txt").apply { writeText("keep") }
            val external = File(root.parentFile, "outside-${UUID.randomUUID()}").apply { writeText("keep") }
            try {
                Os.symlink(external.absolutePath, File(root, "${UUID.randomUUID()}.json").absolutePath)
                store.clear()
                assertEquals("keep", foreign.readText()); assertEquals("keep", external.readText())
            } finally { external.delete() }
        } finally { root.deleteRecursively() }
    }
}
