package io.github.haaaappyday.filmeditor.ui.importphoto

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.MainActivity
import io.github.haaaappyday.filmeditor.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ImportActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realImportSurvivesRotationAndFailedReplacement() {
        lateinit var vm: ImportPhotoViewModel
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity)[ImportPhotoViewModel::class.java]
            vm.importPhoto(Uri.parse("content://io.github.haaaappyday.filmeditor.test.fixtures/orientation-1.jpg"))
        }
        compose.waitUntil(10_000) { vm.state.value.bitmap != null && !vm.state.value.loading }
        val original = vm.state.value.bitmap
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.photo_preview)).performScrollTo().assertIsDisplayed()
        screenshot("import-portrait")
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.photo_preview)).performScrollTo().assertIsDisplayed()
        assertSame(original, vm.state.value.bitmap)
        screenshot("import-landscape")
        compose.runOnUiThread { vm.importPhoto(Uri.parse("content://io.github.haaaappyday.filmeditor.test.fixtures/missing.jpg")) }
        compose.waitUntil(10_000) { vm.state.value.error != null && !vm.state.value.loading }
        assertSame(original, vm.state.value.bitmap)
        compose.onNodeWithText(compose.activity.getString(R.string.retry)).performScrollTo().assertIsDisplayed()
        screenshot("import-error-preserves-photo")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            val directory = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
}
