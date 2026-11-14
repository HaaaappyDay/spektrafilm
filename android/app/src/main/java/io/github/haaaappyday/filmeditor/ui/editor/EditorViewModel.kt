package io.github.haaaappyday.filmeditor.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.haaaappyday.filmeditor.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.math.roundToInt

/** UI entry points run on Main. Bitmaps handed to Compose are released by reference, never recycled. */
class EditorViewModel(
    private val images: ImageRepository,
    private val presets: PresetRepository,
    private val renderer: FilmRenderer,
    private val sessions: SessionPersistence,
    private val exporter: PhotoExporter? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditorUiState(exportAvailable = exporter != null))
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()
    private var closed = false
    private var importVersion = 0L
    private var renderVersion = 0L
    private var importJob: Job? = null
    private var initializeJob: Job? = null
    private var exportJob: Job? = null
    private var lastUri: Uri? = null
    private val renderJobs = mutableSetOf<Job>()
    private data class PreviewRequest(val version: Long, val sourceId: String, val input: Bitmap, val params: EditParams)
    private var pendingPreview: PreviewRequest? = null
    private val retiredSources = mutableMapOf<String, ImageSource>()
    private val writes = Channel<StoredEdit?>(Channel.CONFLATED)
    private val persistenceJob = viewModelScope.launch {
        for (snapshot in writes) {
            try {
                sessions.save(snapshot)
                ensureActive()
                if (snapshot == currentSnapshot() && state.value.errorStage == EditorErrorStage.SESSION) {
                    mutableState.update { it.copy(error = null, errorStage = null) }
                }
                // The serialized writer still references snapshot until the next save.
                // Keep that source and the currently displayed source throughout cleanup.
                for (old in retiredSources.values.toList()) {
                    if (old.id != snapshot?.source?.id && old.id != state.value.source?.id) {
                        try { images.remove(old); retiredSources.remove(old.id) }
                        catch (_: EditorException) { /* Retry at next write or next process startup. */ }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: EditorException) {
                if (snapshot == currentSnapshot() && state.value.error == null) setError(error.code, EditorErrorStage.SESSION)
            }
        }
    }

    init { initialize() }

    private fun initialize() {
        if (closed) return
        initializeJob?.cancel()
        mutableState.update { it.copy(initializing = true, error = null, errorStage = null) }
        initializeJob = viewModelScope.launch {
            var input: Bitmap? = null
            var published = false
            try {
                val available = presets.listPresets()
                ensureActive()
                mutableState.update { it.copy(presets = available) }
                val saved = sessions.load()
                ensureActive()
                if (saved != null) {
                    input = images.decode(saved.source, 1600)
                    ensureActive()
                    mutableState.update { it.copy(source = saved.source, input = input, params = saved.params,
                        initializing = false, preview = null, previewValid = false, exportResult = null, exporting = false) }
                    published = true
                    schedulePreview()
                } else mutableState.update { it.copy(initializing = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: EditorException) {
                // A typed decode/read error does not prove that a durable source is
                // irrecoverable (for example, IMAGE_TOO_LARGE also wraps memory
                // pressure). Retry the same snapshot; a new import can replace it.
                mutableState.update { it.copy(initializing = false, error = error.code, errorStage = EditorErrorStage.INITIALIZE) }
            } finally { if (!published) input?.recycle() }
        }
    }

    fun importPhoto(uri: Uri?) {
        if (uri == null || closed || state.value.initializing || state.value.exporting) return
        lastUri = uri
        val version = ++importVersion
        importJob?.cancel()
        invalidateRenders()
        mutableState.update { it.copy(importing = true, rendering = false, error = null, errorStage = null, exportResult = null) }
        importJob = viewModelScope.launch {
            var source: ImageSource? = null
            var input: Bitmap? = null
            var accepted = false
            try {
                val imported = images.importImage(uri)
                source = imported
                ensureActive()
                input = images.decode(imported, 1600)
                ensureActive()
                if (version != importVersion || closed) return@launch
                val previous = state.value.source
                mutableState.update { it.copy(source = source, input = input, preview = null, params = EditParams("kodak_portra_400"),
                    importing = false, rendering = false, previewValid = false, showOriginal = false, error = null, errorStage = null) }
                accepted = true
                if (previous != null && previous.id != imported.id) retiredSources[previous.id] = previous
                persistCurrent()
                schedulePreview()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: EditorException) {
                if (version == importVersion) {
                    mutableState.update { it.copy(importing = false, error = error.code, errorStage = EditorErrorStage.IMPORT) }
                    if (!state.value.previewValid) schedulePreview()
                }
            } finally {
                if (!accepted) {
                    input?.recycle()
                    source?.let { abandoned -> withContext(NonCancellable) { try { images.remove(abandoned) } catch (_: EditorException) { } } }
                }
            }
        }
    }

    fun setPreset(id: String) {
        if (closed || !state.value.editingEnabled) return
        if (state.value.presets.none { it.id == id }) { invalidateRenders(); failPreview(EditorErrorCode.PRESET_UNAVAILABLE); return }
        updateParams(state.value.params.copy(presetId = id))
    }
    fun setStrength(value: Float) {
        if (value.isFinite()) updateParams(state.value.params.copy(strength = (value.coerceIn(0f, 1f) * 100).roundToInt() / 100f))
    }
    fun setExposure(value: Float) {
        if (value.isFinite()) updateParams(state.value.params.copy(exposureEv = (value.coerceIn(-2f, 2f) * 10).roundToInt() / 10f))
    }
    fun setTemperature(value: Float) {
        if (value.isFinite()) updateParams(state.value.params.copy(temperature = value.coerceIn(-100f, 100f).roundToInt().toFloat()))
    }
    fun reset() { updateParams(EditParams(state.value.params.presetId)) }
    fun toggleOriginal() {
        if (!closed && state.value.editingEnabled) mutableState.update { it.copy(showOriginal = !it.showOriginal) }
    }
    private fun updateParams(params: EditParams) {
        if (closed || !state.value.editingEnabled || (params == state.value.params && state.value.error == null)) return
        mutableState.update { it.copy(params = params, error = null, errorStage = null, exportResult = null, previewValid = false) }
        persistCurrent()
        schedulePreview()
    }
    private fun currentSnapshot(): StoredEdit? = state.value.source?.let { StoredEdit(it, state.value.params) }
    private fun persistCurrent() { writes.trySend(currentSnapshot()) }
    private fun invalidateRenders() {
        renderVersion++
        pendingPreview = null
        renderJobs.toList().forEach { it.cancel() }
    }
    private fun schedulePreview() {
        val current = state.value
        val source = current.source ?: return
        val input = current.input ?: return
        if (closed) return
        val version = ++renderVersion
        if (current.presets.none { it.id == current.params.presetId }) {
            invalidateRenders(); failPreview(EditorErrorCode.PRESET_UNAVAILABLE); return
        }
        pendingPreview = PreviewRequest(version, source.id, input, current.params)
        mutableState.update { it.copy(rendering = true, previewValid = false) }
        renderJobs.toList().forEach { it.cancel() }
        startPendingPreview()
    }
    private fun startPendingPreview() {
        if (closed || renderJobs.size >= 2) return
        val request = pendingPreview ?: return
        pendingPreview = null
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var output: Bitmap? = null
            var published = false
            try {
                output = renderer.render(request.input, request.params)
                ensureActive()
                if (request.version != renderVersion || request.sourceId != state.value.source?.id || request.params != state.value.params) return@launch
                mutableState.update { it.copy(preview = output, previewValid = true, rendering = false,
                    error = if (it.errorStage == EditorErrorStage.PREVIEW) null else it.error,
                    errorStage = if (it.errorStage == EditorErrorStage.PREVIEW) null else it.errorStage) }
                published = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: EditorException) { if (request.version == renderVersion) failPreview(error.code) }
            finally { if (!published) output?.recycle() }
        }
        renderJobs.add(job)
        job.invokeOnCompletion {
            viewModelScope.launch { renderJobs.remove(job); startPendingPreview() }
        }
        job.start()
    }
    private fun failPreview(code: EditorErrorCode) {
        mutableState.update { it.copy(rendering = false, previewValid = false, preview = null, error = code, errorStage = EditorErrorStage.PREVIEW) }
    }
    private fun setError(code: EditorErrorCode, stage: EditorErrorStage) {
        mutableState.update { it.copy(error = code, errorStage = stage) }
    }

    fun export() {
        val saver = exporter ?: return
        val current = state.value
        if (closed || !current.canExport) return
        val request = ExportRequest(checkNotNull(current.source), current.params)
        mutableState.update { it.copy(exporting = true, error = null, errorStage = null, exportResult = null) }
        exportJob = viewModelScope.launch {
            try {
                val result = saver.export(request)
                ensureActive()
                mutableState.update { it.copy(exporting = false, exportResult = result) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: EditorException) { mutableState.update { it.copy(exporting = false, error = error.code, errorStage = EditorErrorStage.EXPORT) } }
        }
    }
    fun retry() {
        if (closed || state.value.exporting || state.value.importing || state.value.initializing) return
        when (state.value.errorStage) {
            EditorErrorStage.INITIALIZE -> initialize()
            EditorErrorStage.IMPORT -> importPhoto(lastUri)
            EditorErrorStage.EXPORT -> export()
            EditorErrorStage.SESSION -> { mutableState.update { it.copy(error = null, errorStage = null) }; persistCurrent() }
            else -> { mutableState.update { it.copy(error = null, errorStage = null) }; persistCurrent(); schedulePreview() }
        }
    }
    override fun onCleared() {
        closed = true
        pendingPreview = null
        writes.close()
        val jobs = renderJobs.toList() + listOfNotNull(importJob, initializeJob, exportJob, persistenceJob)
        jobs.forEach { it.cancel() }
        CoroutineScope(Dispatchers.IO).launch {
            jobs.joinAll()
            renderer.release()
        }
        super.onCleared()
    }
}
