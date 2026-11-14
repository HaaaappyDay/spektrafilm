package io.github.haaaappyday.filmeditor.ui.editor

import android.graphics.Bitmap
import io.github.haaaappyday.filmeditor.core.*

enum class EditorErrorStage { INITIALIZE, IMPORT, PREVIEW, SESSION, EXPORT }

data class EditorUiState(
    val source: ImageSource? = null,
    val input: Bitmap? = null,
    val preview: Bitmap? = null,
    val params: EditParams = EditParams("kodak_portra_400"),
    val presets: List<FilmPreset> = emptyList(),
    val initializing: Boolean = true,
    val servicesReady: Boolean = false,
    val importing: Boolean = false,
    val rendering: Boolean = false,
    val exporting: Boolean = false,
    val showOriginal: Boolean = false,
    val error: EditorErrorCode? = null,
    val errorStage: EditorErrorStage? = null,
    val exportResult: ExportResult? = null,
    val previewValid: Boolean = false,
    val exportAvailable: Boolean = false,
    /** Trace content identities; zero when tracing is disabled. Never persisted. */
    val previewTraceId: Int = 0,
    val exportTraceId: Int = 0,
) {
    val editingEnabled: Boolean get() = servicesReady && source != null && !initializing && !importing && !exporting
    val canImport: Boolean get() = servicesReady && !initializing && !importing && !exporting
    val canExport: Boolean get() = editingEnabled && exportAvailable && previewValid && !rendering
    val displayedBitmap: Bitmap? get() = if (showOriginal || preview == null) input else preview
}
