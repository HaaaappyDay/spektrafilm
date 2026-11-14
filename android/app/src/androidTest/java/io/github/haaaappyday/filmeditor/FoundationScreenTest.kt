package io.github.haaaappyday.filmeditor

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FoundationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun emptyScreenOffersImportAndSurvivesRecreation() {
        val importLabel = compose.activity.getString(R.string.import_photo)
        compose.onNodeWithText(importLabel).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.empty_description)).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(importLabel).assertIsDisplayed().assertIsEnabled()
    }

    @Suppress("DEPRECATION")
    @Test fun offlineAppRequiresGles3WithoutSensitivePermissions() {
        val activity = compose.activity
        val info = activity.packageManager.getPackageInfo(activity.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_CONFIGURATIONS)
        assertTrue(info.requestedPermissions.orEmpty().none { it in setOf(
            "android.permission.INTERNET", "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_IMAGES") })
        assertTrue(info.reqFeatures.orEmpty().any { it.reqGlEsVersion == 0x30000 &&
            it.flags and android.content.pm.FeatureInfo.FLAG_REQUIRED != 0 })
    }
}
