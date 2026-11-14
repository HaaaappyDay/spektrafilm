package io.github.haaaappyday.filmeditor.export

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.MainActivity
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.testsupport.*
import io.github.haaaappyday.filmeditor.ui.editor.EditorViewModel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

class ExportActivityTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(EmptyEditorSessionRule()).around(compose)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    @Test fun offlineRealSaveSurvivesRotationAndOpensPublishedPhoto() {
        val context = instrumentation.targetContext
        val gateway = AndroidMediaStoreGateway(context)
        val previousRows = gateway.rows().map { it.uri }.toSet()
        val oldAirplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
        lateinit var editor: EditorViewModel
        var saved: Uri? = null
        try {
            shell("cmd connectivity airplane-mode enable")
            assertEquals("Airplane mode not enabled", 1, Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0))
            compose.runOnUiThread { editor = ViewModelProvider(compose.activity)[EditorViewModel::class.java] }
            compose.waitUntil(20_000) { !editor.state.value.initializing }
            assertNull(editor.state.value.error)
            compose.runOnUiThread { editor.importPhoto(fixtureUri("12mp.jpg")) }
            compose.waitUntil(30_000) { editor.state.value.previewValid }
            compose.runOnUiThread { editor.setExposure(.7f); editor.setTemperature(30f) }
            compose.waitUntil(30_000) { editor.state.value.previewValid }
            val expected = editor.state.value.params
            compose.onNodeWithTag("export").performScrollTo().assertIsEnabled()
            // Both actions execute in the same Main turn, before IO can publish a result.
            compose.runOnUiThread {
                editor.export(); editor.export()
                assertTrue(editor.state.value.exporting)
                editor.setExposure(-2f)
                compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(90_000) { !editor.state.value.exporting }
            assertNull(editor.state.value.error)
            val result = checkNotNull(editor.state.value.exportResult)
            saved = result.uri
            assertEquals(expected, editor.state.value.params)
            assertEquals(4000, result.width); assertEquals(3000, result.height)
            val added = gateway.rows().filter { it.uri !in previousRows }
            assertEquals(listOf(result.uri), added.map { it.uri }); assertFalse(added.single().pending)
            val sample = context.contentResolver.openInputStream(result.uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 32 })!!
            }
            val expectedColor = try { sample.getPixel(sample.width / 2, sample.height / 2) } finally { sample.recycle() }
            compose.onNodeWithText(context.getString(R.string.open_photo)).performScrollTo().performClick()
            // The result must be displayed by another Activity, not a toast/fake in this app.
            var activities = ""
            compose.waitUntil(15_000) {
                activities = shell("dumpsys activity activities")
                activities.lineSequence().any { (it.contains("mResumedActivity") || it.contains("topResumedActivity")) &&
                    !it.contains(context.packageName) && (it.contains("gallery", true) || it.contains("photos", true) || it.contains("image", true)) }
            }
            // Resumed Activity can precede its first displayed frame. The generated
            // 12MP fixture is uniform: a fitted gallery image covers >30% of either
            // orientation, whereas the previous editor's preview covers <30%.
            var frame: Bitmap? = null
            try {
                compose.waitUntil(15_000) {
                    frame?.recycle()
                    val screen = instrumentation.uiAutomation.takeScreenshot().also { frame = it }
                    var matched = 0; var total = 0
                    val shifts = intArrayOf(0, 8, 16)
                    for (y in 4 until screen.height step 8) for (x in 4 until screen.width step 8) {
                        val pixel = screen.getPixel(x, y); total++
                        if (shifts.all { kotlin.math.abs(((pixel shr it) and 255) - ((expectedColor shr it) and 255)) <= 12 }) matched++
                    }
                    matched > total * .3
                }
            } finally {
                frame?.let { bitmap ->
                    try { File(testOutputDirectory(), "export-system-gallery.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
                    finally { bitmap.recycle() }
                }
            }
            File(testOutputDirectory(), "export-activity-gallery.json").writeText(JSONObject()
                .put("width", result.width).put("height", result.height).put("outputs", added.size)
                .put("airplaneMode", true).put("paramsPreserved", true)
                .put("resumedActivity", activities.lineSequence().filter { it.contains("ResumedActivity") }.joinToString("\n")).toString(2))
        } finally {
            shell("cmd connectivity airplane-mode ${if (oldAirplane == 1) "enable" else "disable"}")
            // Delete only the unique row observed from this test operation.
            saved?.let { uri -> gateway.rows().firstOrNull { it.uri == uri }?.let { gateway.delete(it, false) } }
        }
    }
    @Test fun newEditorCleansInterruptedPendingSaveWithoutReexportingPublishedPhoto() {
        val context = instrumentation.targetContext
        val gateway = AndroidMediaStoreGateway(context)
        var pending: Uri? = null
        var published: Uri? = null
        try {
            lateinit var editor: EditorViewModel
            compose.runOnUiThread { editor = ViewModelProvider(compose.activity)[EditorViewModel::class.java] }
            compose.waitUntil(20_000) { !editor.state.value.initializing }
            compose.runOnUiThread { editor.importPhoto(fixtureUri("orientation-1.jpg")) }
            compose.waitUntil(20_000) { editor.state.value.previewValid }
            compose.onNodeWithTag("export").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(20_000) { editor.state.value.exportResult != null }
            published = editor.state.value.exportResult!!.uri
            val descriptor = File(context.noBackupFilesDir, "editor-session/editor.json")
            compose.waitUntil(10_000) {
                try { JSONObject(descriptor.readText()).getJSONObject("source").getString("id") == editor.state.value.source!!.id }
                catch (_: Exception) { false }
            }
            val operation = PendingExport.create()
            pending = gateway.insert(operation.displayName)
            PendingExportStore(context).write(operation.copy(uri = pending))
            val before = gateway.rows().map { it.uri }.toSet()
            compose.runOnUiThread { compose.activity.viewModelStore.clear() }
            compose.activityRule.scenario.recreate()
            lateinit var restored: EditorViewModel
            compose.runOnUiThread { restored = ViewModelProvider(compose.activity)[EditorViewModel::class.java] }
            compose.waitUntil(20_000) { restored.state.value.previewValid }
            assertNull(restored.state.value.exportResult); assertFalse(restored.state.value.exporting)
            val after = gateway.rows().map { it.uri }.toSet()
            assertEquals(before - pending, after)
            assertTrue(published in after); assertFalse(pending in after)
            assertTrue(PendingExportStore(context).read().isEmpty())
        } finally {
            for (uri in listOfNotNull(pending, published)) gateway.rows().firstOrNull { it.uri == uri }?.let { gateway.delete(it, false) }
        }
    }

}
