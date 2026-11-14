package io.github.haaaappyday.filmeditor.e2e

import android.net.Uri

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.MainActivity
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.core.EditParams
import io.github.haaaappyday.filmeditor.export.AndroidMediaStoreGateway
import io.github.haaaappyday.filmeditor.testsupport.*
import io.github.haaaappyday.filmeditor.ui.editor.EditorViewModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File
import java.security.MessageDigest

class PhotoEditingFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(EmptyEditorSessionRule()).around(compose)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
    private fun slider(tag: String, value: Float) = compose.onNodeWithTag(tag).performScrollTo()
        .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }

    @Test fun allThreeFilmsManualControlsComparisonResetAndOfflineGalleryUseRealServices() {
        val gateway = AndroidMediaStoreGateway(context)
        val outputs = mutableListOf<Uri>()
        val oldAirplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
        lateinit var editor: EditorViewModel
        try {
            shell("cmd connectivity airplane-mode enable")
            assertEquals(1, Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0))
            compose.runOnUiThread { editor = ViewModelProvider(compose.activity)[EditorViewModel::class.java] }
            compose.waitUntil(20_000) { !editor.state.value.initializing }
            assertNull(editor.state.value.error)
            compose.onNodeWithTag("export").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("strength").performScrollTo().assertIsNotEnabled()
            compose.onAllNodesWithText("Auto", substring = true).assertCountEquals(0)
            compose.runOnUiThread { editor.importPhoto(fixtureUri("12mp.jpg")) }
            compose.waitUntil(30_000) { editor.state.value.previewValid }
            val source = checkNotNull(editor.state.value.source)
            val original = hash(File(source.localPath))
            val results = JSONArray()
            assertEquals(3, editor.state.value.presets.size)
            for (preset in editor.state.value.presets) {
                slider("strength", .43f); slider("exposure", .6f); slider("temperature", -35f)
                compose.onNodeWithTag("preset-selector").performScrollTo().performClick()
                compose.onAllNodesWithText(preset.displayName).onLast().performClick()
                compose.waitUntil(30_000) { editor.state.value.previewValid }
                assertEquals(EditParams(preset.id, .43f, .6f, -35f), editor.state.value.params)
                compose.onNodeWithTag("original").performScrollTo().performClick().assertIsSelected()
                assertSame(editor.state.value.input, editor.state.value.displayedBitmap)
                compose.onNodeWithTag("original").performClick().assertIsNotSelected()
                assertSame(editor.state.value.preview, editor.state.value.displayedBitmap)
                compose.onNodeWithTag("reset").performScrollTo().performClick()
                compose.waitUntil(30_000) { editor.state.value.previewValid }
                assertEquals(EditParams(preset.id), editor.state.value.params)
                compose.onNodeWithTag("export").performScrollTo().assertIsEnabled().performClick()
                compose.waitUntil(90_000) { !editor.state.value.exporting }
                assertNull(editor.state.value.error)
                val saved = checkNotNull(editor.state.value.exportResult)
                outputs.add(saved.uri)
                assertEquals(4000, saved.width); assertEquals(3000, saved.height)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(saved.uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
                assertEquals(4000, bounds.outWidth); assertEquals(3000, bounds.outHeight)
                assertEquals(original, hash(File(source.localPath)))
                results.put(JSONObject().put("preset", preset.id).put("manualValuesPreservedOnPresetChange", true)
                    .put("originalToggle", true).put("resetKeptPreset", true).put("width", saved.width).put("height", saved.height))
            }
            assertEquals(3, outputs.toSet().size)
            val saved = editor.state.value.exportResult!!
            val bitmap = context.contentResolver.openInputStream(saved.uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 32 })!!
            }
            val expectedColor = try { bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) } finally { bitmap.recycle() }
            compose.onNodeWithText(context.getString(R.string.open_photo)).performScrollTo().performClick()
            var activities = ""
            compose.waitUntil(15_000) {
                activities = shell("dumpsys activity activities")
                activities.lineSequence().any { (it.contains("mResumedActivity") || it.contains("topResumedActivity")) &&
                    !it.contains(context.packageName) && (it.contains("gallery", true) || it.contains("photos", true) || it.contains("image", true)) }
            }
            var displayed: Bitmap? = null
            try {
                compose.waitUntil(15_000) {
                    displayed?.recycle()
                    val screen = instrumentation.uiAutomation.takeScreenshot().also { displayed = it }
                    var matching = 0; var count = 0
                    for (y in 4 until screen.height step 8) for (x in 4 until screen.width step 8) {
                        val pixel = screen.getPixel(x, y); count++
                        if (intArrayOf(0, 8, 16).all { kotlin.math.abs(((pixel shr it) and 255) - ((expectedColor shr it) and 255)) <= 12 }) matching++
                    }
                    matching > count * .3
                }
                File(testOutputDirectory(), "v1-full-flow-gallery.png").outputStream().use {
                    assertTrue(displayed!!.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { displayed?.recycle() }
            File(testOutputDirectory(), "v1-flow.json").writeText(JSONObject().put("presets", results)
                .put("airplaneMode", true).put("outputs", outputs.size).put("sourceUnchanged", true)
                .put("galleryPhotoDisplayed", true).toString(2))
        } finally {
            shell("cmd connectivity airplane-mode ${if (oldAirplane == 1) "enable" else "disable"}")
            gateway.rows().filter { it.uri in outputs }.forEach { gateway.delete(it, false) }
        }
    }
}
