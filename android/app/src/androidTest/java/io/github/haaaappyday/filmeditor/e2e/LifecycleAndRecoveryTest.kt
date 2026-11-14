package io.github.haaaappyday.filmeditor.e2e

import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.MainActivity
import io.github.haaaappyday.filmeditor.core.EditParams
import io.github.haaaappyday.filmeditor.image.AndroidImageRepository
import io.github.haaaappyday.filmeditor.testsupport.*
import io.github.haaaappyday.filmeditor.ui.editor.EditorViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

class LifecycleAndRecoveryTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(EmptyEditorSessionRule()).around(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun editor(): EditorViewModel {
        lateinit var editor: EditorViewModel
        compose.runOnUiThread { editor = ViewModelProvider(compose.activity)[EditorViewModel::class.java] }
        compose.waitUntil(20_000) { !editor.state.value.initializing }
        return editor
    }
    private fun import(editor: EditorViewModel, fixture: String) {
        compose.runOnUiThread { editor.importPhoto(Uri.parse("content://io.github.haaaappyday.filmeditor.test.fixtures/$fixture")) }
        compose.waitUntil(30_000) { editor.state.value.previewValid && !editor.state.value.importing }
        assertNull(editor.state.value.error)
    }
    @Test fun cancelledAndFailedReplacementPreserveEditsThen48MpImportSamplesAndResets() = runBlocking {
        val editor = editor()
        import(editor, "orientation-1.jpg")
        compose.runOnUiThread { editor.setPreset("kodak_gold_200"); editor.setStrength(.3f); editor.setExposure(.8f); editor.setTemperature(21f) }
        compose.waitUntil(20_000) { editor.state.value.previewValid }
        val previous = editor.state.value
        compose.runOnUiThread { editor.importPhoto(null) }
        assertEquals(previous.source, editor.state.value.source); assertEquals(previous.params, editor.state.value.params)
        compose.runOnUiThread { editor.importPhoto(Uri.parse("content://io.github.haaaappyday.filmeditor.test.fixtures/missing.jpg")) }
        compose.waitUntil(20_000) { !editor.state.value.importing && editor.state.value.error != null }
        assertEquals(previous.source, editor.state.value.source); assertEquals(previous.params, editor.state.value.params)
        assertSame(previous.input, editor.state.value.input)
        import(editor, "48mp.jpg")
        assertNotEquals(previous.source!!.id, editor.state.value.source!!.id)
        assertEquals(EditParams("kodak_portra_400"), editor.state.value.params)
        val preview = editor.state.value.input!!
        assertEquals(1600, preview.width); assertEquals(1200, preview.height)
        val decoded = AndroidImageRepository(context).decode(editor.state.value.source!!, 4096)
        try {
            assertEquals(4096, decoded.width); assertEquals(3072, decoded.height)
            File(testOutputDirectory(), "v1-48mp.json").writeText(JSONObject()
                .put("sourceWidth", 8000).put("sourceHeight", 6000)
                .put("previewWidth", preview.width).put("previewHeight", preview.height)
                .put("exportDecodeWidth", decoded.width).put("exportDecodeHeight", decoded.height)
                .put("newPhotoReset", true).put("failedAndCancelledImportPreservedEdits", true).toString(2))
        } finally { decoded.recycle() }
    }
    @Test fun missingRealPrivateSourceRestoresToImportAndReplacementRecovers() {
        val old = editor(); import(old, "orientation-1.jpg")
        val source = old.state.value.source!!
        val descriptor = File(context.noBackupFilesDir, "editor-session/editor.json")
        compose.waitUntil(10_000) {
            try { JSONObject(descriptor.readText()).getJSONObject("source").getString("id") == source.id }
            catch (_: Exception) { false }
        }
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        assertTrue(File(source.localPath).delete())
        compose.activityRule.scenario.recreate()
        val restored = editor()
        assertNotSame(old, restored)
        assertNotNull(restored.state.value.error); assertNull(restored.state.value.source)
        assertTrue(restored.state.value.canImport); assertFalse(restored.state.value.canExport)
        assertNull(restored.state.value.exportResult)
        import(restored, "orientation-8.jpg")
        assertTrue(restored.state.value.canExport)
        assertEquals(EditParams("kodak_portra_400"), restored.state.value.params)
    }
}
