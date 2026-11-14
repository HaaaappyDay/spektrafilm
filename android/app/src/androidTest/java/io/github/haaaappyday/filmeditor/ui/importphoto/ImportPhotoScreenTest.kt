package io.github.haaaappyday.filmeditor.ui.importphoto

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.core.EditorErrorCode
import io.github.haaaappyday.filmeditor.ui.theme.FilmEditorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ImportPhotoScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun loadingPreventsDuplicateImportAndErrorsOfferRetry() {
        var retries = 0
        compose.setContent { FilmEditorTheme {
            ImportPhotoScreen(null, false, EditorErrorCode.READ_FAILED, {}, { retries++ })
        } }
        compose.onNodeWithText(context.getString(R.string.retry)).performScrollTo().performClick()
        assertEquals(1, retries)
        compose.onNodeWithText(context.getString(R.string.import_photo)).performScrollTo().assertIsEnabled()
    }

    @Test fun loadingHasAccessibleStatusAndDisablesPicker() {
        compose.setContent { FilmEditorTheme { ImportPhotoScreen(null, true, null, {}, {}) } }
        compose.onNodeWithText(context.getString(R.string.importing_photo)).assertExists()
        compose.onNodeWithText(context.getString(R.string.import_photo)).performScrollTo().assertIsNotEnabled()
    }

    @Test fun largeTextKeepsPreviewAndActionsReachable() {
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FilmEditorTheme { ImportPhotoScreen(bitmap, false, null, {}, {}) }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.photo_preview)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.import_photo)).performScrollTo().assertIsDisplayed()
    }
}
