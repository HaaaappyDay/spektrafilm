package io.github.haaaappyday.filmeditor.e2e

import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.*
import android.view.Choreographer
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.BuildConfig
import io.github.haaaappyday.filmeditor.MainActivity
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.diagnostics.PerformanceTrace
import io.github.haaaappyday.filmeditor.export.AndroidMediaStoreGateway
import io.github.haaaappyday.filmeditor.export.PendingExport
import io.github.haaaappyday.filmeditor.testsupport.EmptyEditorSessionRule
import io.github.haaaappyday.filmeditor.testsupport.fixtureUri
import io.github.haaaappyday.filmeditor.testsupport.testOutputDirectory
import io.github.haaaappyday.filmeditor.ui.editor.EditorViewModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Explicit host invocation only: real accessibility actions, no Compose virtual clock. */
class PerformanceScenario {
    @get:Rule val isolatedSession = EmptyEditorSessionRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private lateinit var editor: EditorViewModel

    private fun await(label: String, timeoutMs: Long = 90_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Timed out: $label" }
            SystemClock.sleep(10)
        }
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun settled() {
        instrumentation.waitForIdleSync()
        // Pacing only. These callbacks are never interpreted as presentation evidence.
        val frames = CountDownLatch(1)
        main {
            var remaining = 3
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (--remaining == 0) frames.countDown()
                    else Choreographer.getInstance().postFrameCallback(this)
                }
            }
            Choreographer.getInstance().postFrameCallback(callback)
        }
        check(frames.await(10, TimeUnit.SECONDS)) { "No foreground frames" }
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        // Compose can update geometry before a coalesced accessibility event
        // invalidates UiAutomation's cache. Never search the previous viewport.
        if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (Build.VERSION.SDK_INT < 34 && !node.refresh()) return null
            if (predicate(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
                val found = visit(child)
                if (found != null) return found
            }
            return null
        }
        return automation.rootInActiveWindow?.let(::visit)
    }
    private fun visible(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        await("accessibility window", 15_000) { automation.rootInActiveWindow != null }
        val scrolls = JSONArray()
        fun tree(node: AccessibilityNodeInfo): JSONObject = nodeDetails(node).put("children", JSONArray().apply {
            for (i in 0 until node.childCount) node.getChild(i)?.let { put(tree(it)) }
        })
        fun scroll(action: Int) {
            val node = find { it.isScrollable }
            scrolls.put(JSONObject().put("action", action).put("node", node?.let(::tree) ?: JSONObject.NULL))
            node?.performAction(action)
        }
        var result = find(predicate)
        if (result == null) {
            // Some platform/Compose versions omit clipped semantics nodes.
            repeat(10) {
                if (result == null) {
                    scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    settled(); result = find(predicate)
                }
            }
            repeat(15) {
                if (result == null) {
                    scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    settled(); result = find(predicate)
                }
            }
        }
        if (result == null) {
            val root = automation.rootInActiveWindow
            if (root?.packageName?.toString() == context.packageName) {
                File(testOutputDirectory(), "performance-accessibility.json").writeText(
                    JSONObject().put("scrollAttempts", scrolls).put("root", tree(root)).toString(2))
                automation.takeScreenshot()?.let { bitmap ->
                    try { File(testOutputDirectory(), "performance-screen.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    finally { bitmap.recycle() }
                }
            }
            error("Accessible control not found")
        }
        result!!.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        settled()
        return checkNotNull(find(predicate)) { "Control disappeared" }
    }
    private fun nodeDetails(node: AccessibilityNodeInfo): JSONObject = JSONObject()
        .put("class", node.className).put("text", node.text).put("description", node.contentDescription)
        .put("scrollable", node.isScrollable).put("visible", node.isVisibleToUser)
        .put("bounds", Rect().also(node::getBoundsInScreen).toShortString())
        .put("actions", JSONArray(node.actionList.map { it.id }))
        .put("range", node.rangeInfo?.let { JSONObject().put("min", it.min).put("max", it.max).put("current", it.current) } ?: JSONObject.NULL)
    private fun change(index: Int): JSONObject {
        val label = context.getString(R.string.film_strength)
        var slider = visible { it.contentDescription?.toString() == label }
        // Material3 exposes the label as a child of the actionable SeekBar.
        while (slider.rangeInfo == null || slider.actionList.none {
            it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id
        }) slider = checkNotNull(slider.parent) { "Label has no actionable slider owner" }
        check(slider.rangeInfo.min == 0f && slider.rangeInfo.max == 1f)
        val value = if (index % 2 == 0) .35f else .75f
        val previous = editor.state.value.previewTraceId
        val span = PerformanceTrace.app.begin("FilmInputPreview")
        check(span.id > 0) { "App tracing is not enabled" }
        try {
            val args = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value) }
            check(slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args))
            await("accepted changed preview") {
                val state = editor.state.value
                check(state.error == null) { "Preview failed: ${state.error}" }
                state.previewValid && state.previewTraceId != previous && state.params.strength == value
            }
            settled()
            val state = editor.state.value
            val bitmap = checkNotNull(state.preview)
            return JSONObject().put("inputId", span.id).put("changeId", state.previewTraceId)
                .put("width", bitmap.width).put("height", bitmap.height)
        } finally { span.finish("ended") }
    }
    private fun importFixture() {
        val previous = editor.state.value.source?.id
        main { editor.importPhoto(fixtureUri("12mp.jpg")) }
        await("12MP import") {
            val state = editor.state.value
            check(state.error == null) { "Import failed: ${state.error}" }
            state.previewValid && state.source?.id != previous && !state.importing
        }
        settled()
    }
    private fun snapshot(index: Int, phase: String): JSONObject {
        // Identical post-GC checkpoints for retained-resource trends, outside timed inputs.
        Runtime.getRuntime().gc()
        System.runFinalization()
        SystemClock.sleep(100)
        PerformanceTrace.app.mark("FilmResourceSnapshot", index + 1)
        return memory().put("index", index).put("phase", phase)
            .put("threads", File("/proc/self/task").list()?.size ?: JSONObject.NULL)
            .put("fileDescriptors", File("/proc/self/fd").list()?.size ?: JSONObject.NULL)
    }

    private fun exportOnce(sampler: MemorySampler): JSONObject {
        val label = context.getString(R.string.export_photo)
        var node = visible { it.text?.toString() == label }
        while (!node.isClickable) node = checkNotNull(node.parent)
        val previous = editor.state.value.exportTraceId
        sampler.begin()
        val input = PerformanceTrace.app.begin("FilmInputExport")
        try {
            check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await("published 12MP export", 180_000) {
                val state = editor.state.value
                check(state.error == null) { "Export failed: ${state.error}" }
                !state.exporting && state.exportTraceId != previous && state.exportResult != null
            }
            val state = editor.state.value
            val result = checkNotNull(state.exportResult)
            return JSONObject().put("inputId", input.id).put("exportId", state.exportTraceId)
                .put("sourceWidth", state.source!!.width).put("sourceHeight", state.source!!.height)
                .put("outputWidth", result.width).put("outputHeight", result.height)
                .put("success", true).put("sampleIntervalMs", 100).put("memorySamples", sampler.end())
        } finally { input.finish("ended"); sampler.end() }
    }

    @Test fun warmPreviewImportsAndExports() {
        val app = context.applicationInfo
        check(app.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) { "Measurement requires non-debuggable profileable APK" }
        check(app.isProfileableByShell) { "APK must be profileable by shell" }
        val args = InstrumentationRegistry.getArguments()
        val commit = args.getString("sourceCommit", "")
        check(commit.matches(Regex("[0-9a-f]{40}"))) { "Source commit must be recorded" }
        check(BuildConfig.SOURCE_COMMIT == commit) { "APK source identity differs from requested commit" }
        val report = JSONObject().put("completed", false).put("sourceCommit", commit)
        val previews = JSONArray(); val exports = JSONArray(); val resources = JSONArray(); val warmed = JSONArray()
        report.put("previewActions", previews).put("exportActions", exports)
            .put("resourceSamples", resources).put("warmedPresets", warmed)
            .put("resourceCheckpointPolicy", "post-GC after three real foreground frames; callbacks are pacing only")
        val manager = context.getSystemService(ActivityManager::class.java)
        val ram = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        report.put("device", JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
            .put("fingerprint", Build.FINGERPRINT).put("api", Build.VERSION.SDK_INT)
            .put("totalRamBytes", ram.totalMem).put("javaHeapLimitBytes", Runtime.getRuntime().maxMemory())
            .put("debuggable", false).put("profileable", app.isProfileableByShell)
            .put("package", context.packageName))
        val gateway = AndroidMediaStoreGateway(context)
        val before = gateway.rows().map { it.uri }.toSet()
        val sampler = MemorySampler()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { editor = ViewModelProvider(it)[EditorViewModel::class.java] }
            await("initialization") { !editor.state.value.initializing }
            check(editor.state.value.error == null)
            await("trace enabled", 15_000) { Trace.isEnabled() }
            importFixture()
            // Warm each real renderer/LUT cache before any baseline or measured input.
            for (preset in editor.state.value.presets) {
                main { editor.setPreset(preset.id) }
                await("preset warmup") { editor.state.value.previewValid && editor.state.value.params.presetId == preset.id }
                settled(); warmed.put(preset.id)
            }
            exportOnce(sampler)
            report.put("warmupExports", 1)
            repeat(5) { change(it) }
            report.put("warmupChanges", 5)
            repeat(30) { previews.put(change(it + 5)) }
            resources.put(snapshot(0, "baseline"))
            repeat(20) { index ->
                importFixture(); change(index)
                resources.put(snapshot(index + 1, "import"))
            }
            repeat(10) { index ->
                exports.put(exportOnce(sampler))
                settled(); resources.put(snapshot(21 + index, "export"))
            }
            report.put("completed", true)
        } catch (failure: Throwable) {
            report.put("failureType", failure.javaClass.name)
            throw failure
        } finally {
            sampler.close()
            scenario?.close()
            // Generated outputs from this isolated invocation only; preserve prior photos.
            gateway.rows().filter { it.uri !in before && it.owner == context.packageName &&
                it.path == PendingExport.RELATIVE_PATH && PendingExport.isManagedName(it.name) }
                .forEach { gateway.delete(it, false) }
            report.put("cleanupVerified", gateway.rows().none { it.uri !in before })
            File(testOutputDirectory(), "performance-workload.json").writeText(report.toString(2))
        }
    }
}

private fun memory(): JSONObject {
    val runtime = Runtime.getRuntime()
    val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
    return JSONObject().put("elapsedNs", SystemClock.elapsedRealtimeNanos())
        .put("javaBytes", runtime.totalMemory() - runtime.freeMemory())
        .put("nativeBytes", Debug.getNativeHeapAllocatedSize())
        .put("graphicsPssBytes", info.memoryStats["summary.graphics"]?.toLongOrNull()?.times(1024) ?: JSONObject.NULL)
}

private class MemorySampler : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "FilmMemorySampler") }
    private var active = false
    private var samples = JSONArray()
    init { executor.scheduleAtFixedRate({ synchronized(this) { if (active) samples.put(memory()) } }, 0, 100, TimeUnit.MILLISECONDS) }
    @Synchronized fun begin() { samples = JSONArray().put(memory()); active = true }
    @Synchronized fun end(): JSONArray { if (active) samples.put(memory()); active = false; return samples }
    override fun close() { executor.shutdown(); check(executor.awaitTermination(5, TimeUnit.SECONDS)) }
}
