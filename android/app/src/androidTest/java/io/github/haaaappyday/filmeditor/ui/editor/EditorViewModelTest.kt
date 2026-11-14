package io.github.haaaappyday.filmeditor.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import io.github.haaaappyday.filmeditor.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EditorViewModelTest {
    private class Images : ImageRepository {
        var decodes = 0
        val removed = mutableListOf<String>()
        var missing = false
        override suspend fun importImage(uri: Uri): ImageSource {
            if (uri.lastPathSegment == "bad") throw EditorException(EditorErrorCode.READ_FAILED)
            return ImageSource(uri.toString(), uri.toString(), 4, 3, "image/jpeg")
        }
        override suspend fun decode(source: ImageSource, maxLongEdge: Int): Bitmap {
            if (missing) throw EditorException(EditorErrorCode.READ_FAILED)
            assertEquals(1600, maxLongEdge); decodes++
            return Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff606060.toInt()) }
        }
        override suspend fun remove(source: ImageSource) { removed.add(source.id) }
    }
    private class Sessions : SessionPersistence {
        var saved: StoredEdit? = null
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load() = saved
        override suspend fun save(edit: StoredEdit?) { gate?.await(); saved = edit }
    }
    private class Renderer(var automatic: Boolean = true) : FilmRenderer {
        data class Call(val params: EditParams, val gate: CompletableDeferred<Unit>, var bitmap: Bitmap? = null)
        val calls = mutableListOf<Call>()
        var released = false
        override suspend fun render(input: Bitmap, params: EditParams): Bitmap = withContext(NonCancellable) {
            val call = Call(params, CompletableDeferred()); calls.add(call)
            if (!automatic) call.gate.await()
            Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(0xff000000.toInt() or ((100 + params.exposureEv * 20).toInt() shl 16))
                call.bitmap = this
            }
        }
        override suspend fun release() { released = true }
        fun finishAll() { calls.toList().forEach { it.gate.complete(Unit) } }
    }
    private val presets = object : PresetRepository {
        override suspend fun listPresets() = listOf("kodak_portra_400", "kodak_gold_200", "kodak_ektar_100").map { FilmPreset(it, it, "$it.cube", "0".repeat(64)) }
    }
    private suspend fun await(predicate: () -> Boolean) = withTimeout(10_000) { while (!predicate()) delay(10) }
    private suspend fun main(action: () -> Unit) = withContext(Dispatchers.Main) { action() }
    private suspend fun create(images: Images, renderer: Renderer, sessions: Sessions, exporter: PhotoExporter? = null): Pair<EditorViewModel, ViewModelStore> = withContext(Dispatchers.Main) {
        val vm = EditorViewModel(images, presets, renderer, sessions, exporter)
        vm to ViewModelStore().apply { put("editor", vm) }
    }
    private suspend fun ready(vm: EditorViewModel) {
        await { !vm.state.value.initializing }
        main { vm.importPhoto(Uri.parse("content://fixture/first")) }
        await { vm.state.value.previewValid }
    }

    @Test fun cancelledOldRenderCannotOverwriteNewerResultAndWorkIsBounded() = runBlocking {
        val images = Images(); val renderer = Renderer(); val sessions = Sessions()
        val (vm, store) = create(images, renderer, sessions)
        try {
            ready(vm)
            renderer.automatic = false
            main { vm.setExposure(.1f) }; await { renderer.calls.size == 2 }
            val old = renderer.calls.last()
            main { vm.setExposure(.2f) }; await { renderer.calls.size == 3 }
            val newer = renderer.calls.last(); newer.gate.complete(Unit)
            await { vm.state.value.previewValid }
            val visible = vm.state.value.preview
            old.gate.complete(Unit)
            await { old.bitmap?.isRecycled == true }
            assertSame(visible, vm.state.value.preview)
            assertEquals(.2f, vm.state.value.params.exposureEv)
            val before = renderer.calls.size
            main { repeat(100) { vm.setTemperature((it - 50).toFloat()) } }
            delay(50)
            assertTrue("Unbounded render work", renderer.calls.size - before <= 2)
            repeat(4) { renderer.finishAll(); delay(50) }
            await { vm.state.value.previewValid && vm.state.value.params.temperature == 49f }
            assertTrue(renderer.calls.size - before <= 3)
            assertEquals(1, images.decodes)
        } finally { renderer.automatic = true; renderer.finishAll(); main { store.clear() } }
    }

    @Test fun lateOldPhotoCannotReplaceSuccessfulNewImport() = runBlocking {
        val images = Images(); val renderer = Renderer(); val (vm, store) = create(images, renderer, Sessions())
        try {
            ready(vm); renderer.automatic = false
            main { vm.setExposure(1f) }; await { renderer.calls.size == 2 }
            val old = renderer.calls.last()
            main { vm.importPhoto(Uri.parse("content://fixture/second")) }
            await { renderer.calls.size == 3 }
            renderer.calls.last().gate.complete(Unit)
            await { vm.state.value.previewValid && vm.state.value.source?.id?.endsWith("second") == true }
            val current = vm.state.value.preview
            old.gate.complete(Unit); await { old.bitmap?.isRecycled == true }
            assertSame(current, vm.state.value.preview)
            assertEquals(EditParams("kodak_portra_400"), vm.state.value.params)
        } finally { renderer.automatic = true; renderer.finishAll(); main { store.clear() } }
    }

    @Test fun valuesResetComparisonAndFailedImportFollowTheContract() = runBlocking {
        val images = Images(); val renderer = Renderer(); val sessions = Sessions()
        val (vm, store) = create(images, renderer, sessions)
        try {
            ready(vm)
            main { vm.setStrength(.37f); vm.setExposure(.34f); vm.setTemperature(23.6f); vm.setPreset("kodak_gold_200") }
            await { vm.state.value.previewValid }
            assertEquals(EditParams("kodak_gold_200", .37f, .3f, 24f), vm.state.value.params)
            val source = vm.state.value.source; val params = vm.state.value.params
            main { vm.importPhoto(null); vm.importPhoto(Uri.parse("content://fixture/bad")) }
            await { vm.state.value.error != null && !vm.state.value.importing }
            assertEquals(source, vm.state.value.source); assertEquals(params, vm.state.value.params)
            main { vm.setStrength(0f); vm.toggleOriginal() }
            await { vm.state.value.previewValid }
            assertTrue(vm.state.value.showOriginal)
            assertEquals(.3f, vm.state.value.params.exposureEv)
            assertNotSame(vm.state.value.input, vm.state.value.preview)
            main { vm.reset() }; await { vm.state.value.previewValid }
            assertEquals(EditParams("kodak_gold_200"), vm.state.value.params)
            assertEquals(1, images.decodes)
        } finally { main { store.clear() } }
    }

    @Test fun persistenceCompletesBeforeRetiringOldSourceAndRestoresWithoutSavingAgain() = runBlocking {
        val images = Images(); val renderer = Renderer(); val sessions = Sessions()
        val (vm, store) = create(images, renderer, sessions)
        var restoredStore: ViewModelStore? = null
        try {
            ready(vm); await { sessions.saved != null }
            val old = vm.state.value.source!!
            sessions.gate = CompletableDeferred()
            main { vm.importPhoto(Uri.parse("content://fixture/second")); vm.setExposure(1f) }
            await { vm.state.value.source?.id?.endsWith("second") == true }
            assertFalse(images.removed.contains(old.id))
            sessions.gate!!.complete(Unit)
            main { vm.setExposure(.7f) }
            await { sessions.saved?.params?.exposureEv == .7f && images.removed.contains(old.id) }
            main { store.clear() }
            val (restored, owner) = create(images, Renderer(), sessions)
            restoredStore = owner
            await { restored.state.value.previewValid }
            assertEquals(sessions.saved!!.source, restored.state.value.source)
            assertEquals(sessions.saved!!.params, restored.state.value.params)
            assertFalse(restored.state.value.exporting); assertNull(restored.state.value.exportResult)
        } finally { sessions.gate?.complete(Unit); main { store.clear(); restoredStore?.clear() } }
    }

    @Test fun missingRestoreSourceIsExplainedAndCannotExport() = runBlocking {
        val images = Images().apply { missing = true }
        val sessions = Sessions().apply { saved = StoredEdit(ImageSource("gone", "/gone", 4, 3, "image/jpeg"), EditParams("kodak_portra_400")) }
        val (vm, store) = create(images, Renderer(), sessions)
        try {
            await { !vm.state.value.initializing }
            assertNull(vm.state.value.source); assertEquals(EditorErrorCode.READ_FAILED, vm.state.value.error)
            assertFalse(vm.state.value.canExport)
            await { sessions.saved == null }
        } finally { main { store.clear() } }
    }

    @Test fun exportingLocksEditsCapturesOneSnapshotAndFailureCanRetry() = runBlocking {
        val requests = mutableListOf<ExportRequest>(); var fail = true
        var gate = CompletableDeferred<Unit>()
        val exporter = object : PhotoExporter {
            override suspend fun export(request: ExportRequest): ExportResult {
                requests.add(request); gate.await()
                if (fail) throw EditorException(EditorErrorCode.EXPORT_FAILED)
                return ExportResult(Uri.parse("content://gallery/output"), 4, 3)
            }
        }
        val (vm, store) = create(Images(), Renderer(), Sessions(), exporter)
        try {
            ready(vm)
            val expected = ExportRequest(vm.state.value.source!!, vm.state.value.params)
            main { vm.export(); vm.export(); vm.setExposure(2f); vm.importPhoto(Uri.parse("content://fixture/second")); vm.reset() }
            await { requests.size == 1 }
            assertTrue(vm.state.value.exporting); assertEquals(expected, requests.single())
            assertEquals(expected.source, vm.state.value.source); assertEquals(expected.params, vm.state.value.params)
            gate.complete(Unit); await { vm.state.value.error == EditorErrorCode.EXPORT_FAILED }
            assertNull(vm.state.value.exportResult)
            fail = false; gate = CompletableDeferred<Unit>().apply { complete(Unit) }
            main { vm.retry() }
            await { vm.state.value.exportResult != null }
            assertEquals(2, requests.size); assertEquals(expected, requests.last())
        } finally { gate.complete(Unit); main { store.clear() } }
    }
}
