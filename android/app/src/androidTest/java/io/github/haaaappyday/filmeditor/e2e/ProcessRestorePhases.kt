package io.github.haaaappyday.filmeditor.e2e

import android.net.Uri
import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.MainActivity
import io.github.haaaappyday.filmeditor.core.EditParams
import io.github.haaaappyday.filmeditor.export.AndroidMediaStoreGateway
import io.github.haaaappyday.filmeditor.export.PendingExport
import io.github.haaaappyday.filmeditor.export.PendingExportStore
import io.github.haaaappyday.filmeditor.testsupport.fixtureUri
import io.github.haaaappyday.filmeditor.ui.editor.EditorSessionStore
import io.github.haaaappyday.filmeditor.ui.editor.EditorViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Invoked only by verify_android_process_restore.py, in separate OS processes.
 * prepare intentionally stays alive until the host kills it after the ready marker.
 */
class ProcessRestorePhases {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val gateway = AndroidMediaStoreGateway(context)
    private val stateFile = File(context.filesDir, "process-probe-private.json")
    private val expected = EditParams("kodak_gold_200", .43f, .6f, -35f)

    private fun requirePhase(name: String): String {
        assumeTrue("Host phase driver only", args.getString("probeMode") == name)
        check(context.packageName == "io.github.haaaappyday.filmeditor.validation")
        return requireNotNull(args.getString("probeRunId")).also { require(it.matches(Regex("[0-9a-f]{32}"))) }
    }
    private fun waitFor(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (!predicate()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Timed out waiting for editor state" }
            Thread.sleep(25)
        }
    }
    private fun editor(scenario: ActivityScenario<MainActivity>): EditorViewModel {
        lateinit var editor: EditorViewModel
        scenario.onActivity { editor = ViewModelProvider(it)[EditorViewModel::class.java] }
        waitFor { !editor.state.value.initializing }
        assertNull(editor.state.value.error)
        return editor
    }
    private fun persist(file: File, data: JSONObject) {
        val staged = File(file.path + ".new")
        FileOutputStream(staged).use { it.write(data.toString().toByteArray()); it.fd.sync() }
        check(staged.renameTo(file))
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private fun photoHash(uri: String) = digest(context.contentResolver.openInputStream(Uri.parse(uri))!!.use { it.readBytes() })
    private fun identity(runId: String) = JSONObject().put("schemaVersion", 1)
        .put("package", context.packageName).put("runId", runId)

    @Test fun prepare() = runBlocking {
        val runId = requirePhase("prepare")
        // Only the isolated fixture app's descriptor is reset.
        EditorSessionStore(context).save(null)
        val state = identity(runId).put("preparedPid", Process.myPid())
        persist(stateFile, state)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val editor = editor(scenario)
            scenario.onActivity { editor.importPhoto(fixtureUri("orientation-1.jpg")) }
            waitFor { editor.state.value.previewValid && !editor.state.value.importing }
            scenario.onActivity {
                editor.setPreset(expected.presetId); editor.setStrength(expected.strength)
                editor.setExposure(expected.exposureEv); editor.setTemperature(expected.temperature)
            }
            waitFor { editor.state.value.previewValid && editor.state.value.params == expected }
            val source = editor.state.value.source!!
            waitFor { runBlocking { EditorSessionStore(context).load()?.let { it.source == source && it.params == expected } == true } }
            state.put("sourceId", source.id).put("sourcePath", source.localPath)
                .put("sourceHash", digest(File(source.localPath).readBytes()))
            persist(stateFile, state)
            scenario.onActivity { editor.export() }
            waitFor { editor.state.value.exportResult != null && !editor.state.value.exporting }
            assertNull(editor.state.value.error)
            val published = editor.state.value.exportResult!!.uri.toString()
            state.put("publishedUri", published).put("publishedHash", photoHash(published))
            persist(stateFile, state)
            // Model death after pending insertion and durable URI journaling, but
            // before publication. A completed published output must survive too.
            val pending = PendingExport.create()
            state.put("pendingName", pending.displayName)
            persist(stateFile, state)
            val uri = gateway.insert(pending.displayName)
            gateway.open(uri).use { output ->
                instrumentation.context.assets.open("import-fixtures/orientation-1.jpg").use { it.copyTo(output) }
            }
            PendingExportStore(context).write(pending.copy(uri = uri))
            state.put("pendingUri", uri.toString())
                .put("publishedCount", gateway.rows().count { !it.pending })
            persist(stateFile, state)
            persist(File(context.filesDir, "process-probe-ready.json"), identity(runId)
                .put("preparedPid", Process.myPid()).put("readyForKill", true))
            // A normal return would weaken this into two orderly launches. Only
            // the host's force-stop should terminate this live instrumentation.
            Thread.sleep(180_000)
            fail("Host did not force-stop the prepared live process")
        }
    }

    @Test fun verify() {
        val runId = requirePhase("verify")
        val state = JSONObject(stateFile.readText())
        assertEquals(runId, state.getString("runId"))
        assertNotEquals(state.getInt("preparedPid"), Process.myPid())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val editor = editor(scenario)
            waitFor { editor.state.value.previewValid }
            val restored = editor.state.value
            val source = restored.source!!
            assertEquals(state.getString("sourceId"), source.id)
            assertEquals(state.getString("sourcePath"), source.localPath)
            assertEquals(state.getString("sourceHash"), digest(File(source.localPath).readBytes()))
            assertEquals(expected, restored.params)
            assertNotNull(restored.preview); assertNull(restored.exportResult)
            assertFalse(restored.exporting); assertNull(restored.error)
            val rows = gateway.rows()
            assertFalse(rows.any { it.uri.toString() == state.getString("pendingUri") })
            assertTrue(rows.any { !it.pending && it.uri.toString() == state.getString("publishedUri") })
            assertEquals(state.getString("publishedHash"), photoHash(state.getString("publishedUri")))
            assertEquals(state.getInt("publishedCount"), rows.count { !it.pending })
            assertTrue(PendingExportStore(context).read().isEmpty())
            val result = identity(runId).put("preparedPid", state.getInt("preparedPid"))
                .put("restoredPid", Process.myPid()).put("sourcePreserved", true)
                .put("paramsRestored", true).put("pendingRemoved", true).put("publishedPreserved", true)
                .put("noDuplicateExport", true).put("previewRestored", true).put("noTransientSuccess", true)
            persist(File(context.filesDir, "process-probe-result.json"), result)
        }
    }

    @Test fun cleanup() = runBlocking {
        val runId = requirePhase("cleanup")
        if (!stateFile.exists()) return@runBlocking
        val state = JSONObject(stateFile.readText())
        if (state.getString("runId") != runId) return@runBlocking
        gateway.rows().filter { it.uri.toString() == state.optString("publishedUri") ||
            it.name == state.optString("pendingName") }.forEach { gateway.delete(it, false) }
        EditorSessionStore(context).save(null)
        state.optString("sourcePath").takeIf { it.isNotEmpty() }?.let { path ->
            val file = File(path)
            check(file.parentFile!!.canonicalFile == File(context.noBackupFilesDir, "sources").canonicalFile)
            check(file.name == "${state.getString("sourceId")}.source")
            check(!file.exists() || file.delete())
        }
        PendingExportStore(context).clear()
        check(stateFile.delete())
        File(context.filesDir, "process-probe-ready.json").delete()
    }
}
