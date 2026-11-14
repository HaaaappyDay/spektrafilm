package io.github.haaaappyday.filmeditor.ui.editor

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.MainActivity
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.testsupport.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File
import java.util.UUID

class EditorActivityTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(EmptyEditorSessionRule()).around(compose)
    private fun vm(): EditorViewModel = ViewModelProvider(compose.activity)[EditorViewModel::class.java]
    private fun import(): EditorViewModel {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        lateinit var editor: EditorViewModel
        compose.runOnUiThread { editor = vm() }
        compose.waitUntil(15_000) { !editor.state.value.initializing }
        compose.runOnUiThread { editor.importPhoto(Uri.parse("content://io.github.haaaappyday.filmeditor.test.fixtures/orientation-1.jpg")) }
        compose.waitUntil(15_000) { editor.state.value.previewValid }
        return editor
    }
    @Test fun realEditorControlsRotationBackgroundAndFailedReplacement() {
        val editor = import()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val path = "/sdcard/Download/FilmEditor-test-${UUID.randomUUID()}.mp4"
        val recording = automation.executeShellCommand("screenrecord --bit-rate 1000000 --time-limit 12 $path")
        try {
            compose.onNodeWithTag("strength").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.4f) }
            compose.onNodeWithTag("exposure").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.7f) }
            compose.onNodeWithTag("temperature").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
            compose.onNodeWithTag("preset-selector").performScrollTo().performClick()
            compose.onNodeWithText("Kodak Gold 200").performClick()
            compose.waitUntil(15_000) { editor.state.value.previewValid }
            compose.onNodeWithTag("original").performScrollTo().performClick()
            assertTrue(editor.state.value.showOriginal)
            compose.onNodeWithTag("original").performClick()
            val params = editor.state.value.params
            val input = editor.state.value.input
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.photo_preview)).assertIsDisplayed()
            screenshot("editor-portrait")
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.photo_preview)).assertIsDisplayed()
            assertSame(input, editor.state.value.input); assertEquals(params, editor.state.value.params)
            screenshot("editor-landscape")
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.runOnUiThread { editor.importPhoto(Uri.parse("content://io.github.haaaappyday.filmeditor.test.fixtures/missing.jpg")) }
            compose.waitUntil(10_000) { editor.state.value.error != null && !editor.state.value.importing }
            assertSame(input, editor.state.value.input); assertEquals(params, editor.state.value.params)
            compose.onNodeWithTag("retry").performScrollTo().assertIsDisplayed()
            screenshot("editor-error-preserves-photo")
        } finally {
            val output = testOutputDirectory()
            ParcelFileDescriptor.AutoCloseInputStream(recording).use { input -> File(output, "editor-screenrecord.log").outputStream().use { input.copyTo(it) } }
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cat $path")).use { input ->
                File(output, "editor-interaction.mp4").outputStream().use { input.copyTo(it) }
            }
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("rm -f $path")).use { it.readBytes() }
            assertTrue("Interaction recording missing", File(output, "editor-interaction.mp4").length() > 1024)
        }
    }
    @Test fun newViewModelRestoresActualPrivateSourceAndParameters() {
        val previous = import()
        compose.runOnUiThread { previous.setExposure(-.6f); previous.setTemperature(-31f) }
        compose.waitUntil(10_000) { previous.state.value.previewValid }
        val file = File(compose.activity.noBackupFilesDir, "editor-session/editor.json")
        compose.waitUntil(10_000) {
            try { JSONObject(file.readText()).getJSONObject("params").getDouble("exposureEv").toFloat() == -.6f }
            catch (_: Exception) { false }
        }
        val expected = previous.state.value.params
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        compose.activityRule.scenario.recreate()
        lateinit var restored: EditorViewModel
        compose.runOnUiThread { restored = vm() }
        compose.waitUntil(15_000) { restored.state.value.previewValid }
        assertNotSame(previous, restored); assertNotSame(previous.state.value.input, restored.state.value.input)
        assertEquals(expected, restored.state.value.params)
        assertFalse(restored.state.value.exporting); assertNull(restored.state.value.exportResult)
    }
    private fun screenshot(name: String) {
        compose.waitForIdle(); compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try { File(testOutputDirectory(), "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }
}
