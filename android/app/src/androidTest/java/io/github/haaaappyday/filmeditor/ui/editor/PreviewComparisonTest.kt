package io.github.haaaappyday.filmeditor.ui.editor

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.core.ImageSource
import io.github.haaaappyday.filmeditor.ui.theme.FilmEditorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PreviewComparisonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val original = 0xff2040c0.toInt()
    private val edited = 0xffc04020.toInt()
    private fun bitmap(color: Int) = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private var state by mutableStateOf(EditorUiState(
        source = ImageSource("one", "/one", 80, 60, "image/jpeg"), input = bitmap(original), preview = bitmap(edited),
        initializing = false, servicesReady = true, previewValid = true, exportAvailable = true))
    private val restoration = StateRestorationTester(compose)
    private fun content() = restoration.setContent { FilmEditorTheme {
        EditorScreen(state, {}, {}, {}, {}, {}, {}, { state = state.copy(showOriginal = !state.showOriginal) }, {}, {}, {})
    } }
    private fun photo() = compose.onNodeWithContentDescription(context.getString(R.string.photo_preview))
    private fun color(): Int {
        val bitmap = photo().captureToImage().asAndroidBitmap()
        return try { bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) } finally { bitmap.recycle() }
    }
    private fun hold() {
        photo().performTouchInput { down(center); advanceEventTime(750) }
        compose.mainClock.advanceTimeBy(750)
    }

    @Test fun holdShowsUneditedInputAndReleaseOrCancelRestoresEditedPixels() {
        content()
        val params = state.params
        val bounds = photo().fetchSemanticsNode().boundsInRoot
        photo().performTouchInput { click() }
        assertEquals(edited, color())
        hold()
        assertEquals(original, color())
        assertEquals(bounds, photo().fetchSemanticsNode().boundsInRoot)
        assertFalse(state.showOriginal)
        photo().performTouchInput { up() }
        assertEquals(edited, color())
        hold()
        assertEquals(original, color())
        photo().performTouchInput { cancel() }
        assertEquals(edited, color())
        assertEquals(params, state.params)
    }

    @Test fun holdDoesNotChangeExistingToolbarComparisonSelection() {
        content()
        compose.onNodeWithTag("original").performClick().assertIsSelected()
        hold()
        assertEquals(original, color())
        photo().performTouchInput { up() }
        assertEquals(original, color())
        compose.onNodeWithTag("original").assertIsSelected().performClick()
        assertEquals(edited, color())
    }

    @Test fun sourceReplacementAndExportLockCancelTemporaryComparison() {
        content()
        hold()
        assertEquals(original, color())
        val replacement = 0xff208040.toInt()
        compose.runOnIdle { state = state.copy(source = state.source!!.copy(id = "two"),
            input = bitmap(0xffe0d020.toInt()), preview = bitmap(replacement)) }
        assertEquals(replacement, color())
        photo().performTouchInput { up() }
        hold()
        assertEquals(0xffe0d020.toInt(), color())
        compose.runOnIdle { state = state.copy(exporting = true) }
        assertEquals(replacement, color())
        photo().performTouchInput { up() }
        hold()
        assertEquals(replacement, color())
        photo().performTouchInput { up() }
    }

    @Test fun backgroundAndRecreationDoNotRestoreAHeldGesture() {
        content()
        hold()
        assertEquals(original, color())
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals(edited, color())
        photo().performTouchInput { cancel() }
        hold()
        assertEquals(original, color())
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(edited, color())
    }
}
