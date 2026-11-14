package io.github.haaaappyday.filmeditor.ui.importphoto

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.haaaappyday.filmeditor.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Base-preview state only. The full editor/session persistence is introduced by #13. */
data class ImportPhotoState(
    val source: ImageSource? = null,
    val bitmap: Bitmap? = null,
    val loading: Boolean = false,
    val error: EditorErrorCode? = null,
)

class ImportPhotoViewModel(private val images: ImageRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ImportPhotoState())
    val state: StateFlow<ImportPhotoState> = mutableState.asStateFlow()
    private var request = 0L
    private var job: Job? = null
    private var lastUri: Uri? = null

    fun importPhoto(uri: Uri?) {
        if (uri == null) return // picker cancellation is not a failed import
        lastUri = uri
        val version = ++request
        job?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            var candidate: ImageSource? = null
            var bitmap: Bitmap? = null
            var accepted = false
            try {
                candidate = images.importImage(uri)
                ensureActive()
                bitmap = images.decode(candidate, 1600)
                ensureActive()
                if (version != request) return@launch
                val old = mutableState.value.source
                mutableState.value = ImportPhotoState(candidate, bitmap)
                accepted = true
                // Drop old bitmap references without recycling: Compose may still draw it.
                if (old != null && old.id != candidate.id) removeUnused(old)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: EditorException) {
                if (version == request) mutableState.update { it.copy(loading = false, error = error.code) }
            } finally {
                if (!accepted) {
                    bitmap?.recycle() // never handed to Compose
                    candidate?.let { removeUnused(it) }
                }
            }
        }
    }

    fun retry() { importPhoto(lastUri) }

    private suspend fun removeUnused(source: ImageSource) {
        withContext(NonCancellable) {
            try { images.remove(source) } catch (_: EditorException) {
                // A cleanup failure must not replace a successful preview or delete its source.
                // Completed orphan-source collection belongs to the durable session owner (#13).
            }
        }
    }
}
