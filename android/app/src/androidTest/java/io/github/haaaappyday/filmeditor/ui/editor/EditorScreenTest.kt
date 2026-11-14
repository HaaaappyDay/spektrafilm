package io.github.haaaappyday.filmeditor.ui.editor

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.core.*
import io.github.haaaappyday.filmeditor.ui.theme.FilmEditorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EditorScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun photo() = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(0xffabc123.toInt()) }
    private fun ready(): EditorUiState {
        val bitmap = photo()
        return EditorUiState(source = ImageSource("source", "/source", 6000, 4000, "image/jpeg"),
            input = bitmap, preview = bitmap, initializing = false, previewValid = true, exportAvailable = true,
            presets = listOf(FilmPreset("kodak_portra_400", "Portra 400", "p", "0".repeat(64)), FilmPreset("kodak_gold_200", "Gold 200", "g", "1".repeat(64))))
    }
    @Test fun noPhotoDisablesEditingAndSaving() {
        compose.setContent { FilmEditorTheme {
            EditorScreen(EditorUiState(initializing = false), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("import").assertIsEnabled()
        for (tag in listOf("preset-selector", "strength", "exposure", "temperature", "reset", "original", "export")) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled()
        }
    }
    @Test fun accessibleSlidersPresetResetComparisonAndSaveLockout() {
        var state by mutableStateOf(ready())
        var exports = 0
        compose.setContent { FilmEditorTheme {
            EditorScreen(state, {}, { state = state.copy(params = state.params.copy(presetId = it)) },
                { state = state.copy(params = state.params.copy(strength = it)) },
                { state = state.copy(params = state.params.copy(exposureEv = it)) },
                { state = state.copy(params = state.params.copy(temperature = it)) },
                { state = state.copy(params = EditParams(state.params.presetId)) },
                { state = state.copy(showOriginal = !state.showOriginal) }, {},
                { exports++; state = state.copy(exporting = true) }, {})
        } }
        compose.onNodeWithTag("strength").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.37f) }
        compose.onNodeWithTag("exposure").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.34f) }
        compose.onNodeWithTag("temperature").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(24f) }
        compose.onNodeWithTag("preset-selector").performScrollTo().performClick()
        compose.onNodeWithText("Gold 200").performClick()
        compose.runOnIdle { assertEquals(EditParams("kodak_gold_200", .37f, .3f, 24f), state.params) }
        compose.onNodeWithTag("original").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(state.showOriginal) }
        compose.onNodeWithTag("reset").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(EditParams("kodak_gold_200"), state.params) }
        compose.onNodeWithTag("export").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, exports) }
        for (tag in listOf("import", "preset-selector", "strength", "exposure", "temperature", "reset", "original", "export")) {
            val node = compose.onNodeWithTag(tag)
            if (tag != "import") node.performScrollTo()
            node.assertIsNotEnabled()
        }
    }
    @Test fun failureAndLargeTextExposeRetryAndActualOutputDimensions() {
        var retries = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                FilmEditorTheme { EditorScreen(ready().copy(previewValid = false, preview = null, error = EditorErrorCode.RENDER_FAILED,
                    errorStage = EditorErrorStage.PREVIEW), {}, {}, {}, {}, {}, {}, {}, { retries++ }, {}, {}) }
            }
        }
        compose.onNodeWithTag("export").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.export_dimensions, 4096, 2731)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("retry").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithContentDescription(context.getString(R.string.photo_preview)).assertIsDisplayed()
    }
}
