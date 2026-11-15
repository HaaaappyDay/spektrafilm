package io.github.haaaappyday.filmeditor.ui.editor

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.haaaappyday.filmeditor.testsupport.testOutputDirectory
import java.io.File
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso
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
            input = bitmap, preview = bitmap, initializing = false, servicesReady = true, previewValid = true, exportAvailable = true,
            presets = listOf(FilmPreset("kodak_portra_400", "Portra 400", "p", "0".repeat(64)), FilmPreset("kodak_gold_200", "Gold 200", "g", "1".repeat(64))))
    }
    private fun tab(name: String) = compose.onNodeWithTag("tab-$name").performClick()
    private fun adjust(name: String, value: Float) {
        compose.onNodeWithTag("adjust-$name").performScrollTo().performClick()
        compose.onNodeWithTag(name).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }
    @Test fun emptyEditorHasAClearImportWithoutInactiveParameterClutter() {
        var imports = 0
        compose.setContent { FilmEditorTheme {
            EditorScreen(EditorUiState(initializing = false, servicesReady = true), { imports++ }, {}, {}, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("import").assertIsEnabled().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, imports) }
        for (tag in listOf("strength", "exposure", "temperature", "reset", "original", "export", "tab-films")) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
    }
    @Test fun accessibleSlidersFilmCardsComparisonResetAndSaveLockout() {
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
        tab("adjust")
        adjust("strength", .37f); adjust("exposure", .34f); adjust("temperature", 24f)
        tab("films")
        compose.onNodeWithTag("preset-kodak_gold_200").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(EditParams("kodak_gold_200", .37f, .3f, 24f), state.params) }
        compose.onNodeWithTag("original").performClick().assertIsSelected()
        compose.runOnIdle { assertTrue(state.showOriginal) }
        tab("adjust")
        compose.onNodeWithTag("reset").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(EditParams("kodak_gold_200"), state.params) }
        tab("export")
        compose.onNodeWithTag("export").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, exports) }
        for (tag in listOf("import", "original", "tab-films", "tab-adjust", "tab-export", "export")) {
            compose.onNodeWithTag(tag).assertIsNotEnabled()
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
        compose.onNodeWithTag("retry").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        tab("export")
        compose.onNodeWithTag("export").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.export_dimensions, 4096, 2731)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.photo_preview)).assertIsDisplayed()
        screenshot("editor-large-text-export")
    }
    @Test fun toolNavigationAndRestorationPreserveEditsWithoutSaving() {
        var state by mutableStateOf(ready())
        var exports = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FilmEditorTheme {
            EditorScreen(state, {}, {}, {}, { state = state.copy(params = state.params.copy(exposureEv = it)) },
                {}, {}, {}, {}, { exports++ }, {})
        } }
        tab("export")
        compose.runOnIdle { assertEquals(0, exports); assertEquals(EditParams("kodak_portra_400"), state.params) }
        tab("adjust")
        compose.onNodeWithTag("strength").assertExists()
        compose.onNodeWithTag("exposure").assertDoesNotExist()
        adjust("exposure", .4f)
        compose.onNodeWithTag("strength").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("tab-adjust").assertIsSelected()
        compose.onNodeWithTag("exposure").assertExists()
        compose.runOnIdle { assertEquals(.4f, state.params.exposureEv); assertEquals(0, exports) }
        Espresso.pressBack()
        compose.onNodeWithTag("tab-films").assertIsSelected()
        compose.runOnIdle { assertEquals(.4f, state.params.exposureEv); assertEquals(0, exports) }
    }
    @Test fun contrastAndTintUseTheSameSliderWithoutChangingTheFilm() {
        var state by mutableStateOf(ready())
        compose.setContent { FilmEditorTheme {
            EditorScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onContrast = { state = state.copy(params = state.params.copy(contrast = it)) },
                onTint = { state = state.copy(params = state.params.copy(tint = it)) })
        } }
        tab("adjust")
        adjust("contrast", 25f)
        adjust("tint", -10f)
        compose.runOnIdle {
            assertEquals("kodak_portra_400", state.params.presetId)
            assertEquals(25f, state.params.contrast)
            assertEquals(-10f, state.params.tint)
            assertEquals(1f, state.params.strength)
        }
    }
    @Test fun normalPhoneLayoutGivesMostOfTheWorkspaceToThePhoto() {
        compose.setContent { FilmEditorTheme { EditorScreen(ready(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag("photo-workspace").fetchSemanticsNode().boundsInRoot
        assertTrue("Photo workspace $preview in $root", preview.height >= root.height * .55f)
        compose.onNodeWithTag("tab-films").assertIsDisplayed()
        compose.onNodeWithTag("preset-kodak_portra_400").assertIsDisplayed().assertIsSelected()
        screenshot("editor-workspace")
    }
    @Test fun loadingFeedbackKeepsExactPhotoBoundsAtNormalAndLargeText() {
        val base = ready()
        var state by mutableStateOf(base)
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                FilmEditorTheme { EditorScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
            }
        }
        for (fontScale in listOf(1f, 2f)) {
            compose.runOnIdle { scale = fontScale; state = base }
            val photo = compose.onNodeWithContentDescription(context.getString(R.string.photo_preview))
            val bounds = photo.fetchSemanticsNode().boundsInRoot
            for (busy in listOf(base.copy(rendering = true), base.copy(importing = true),
                base.copy(exporting = true), base.copy(initializing = true), base.copy(showOriginal = true), base)) {
                compose.runOnIdle { state = busy }
                assertEquals("Photo moved with font scale $fontScale and busy state $busy", bounds,
                    photo.fetchSemanticsNode().boundsInRoot)
            }
        }
    }
    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try { File(testOutputDirectory(), "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }
}
