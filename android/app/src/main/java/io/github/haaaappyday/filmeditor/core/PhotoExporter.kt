package io.github.haaaappyday.filmeditor.core

import android.net.Uri

/** Immutable source + parameters captured at the user's export action. */
data class ExportRequest(val source: ImageSource, val params: EditParams)
/** Published, readable gallery URI and actual output dimensions; never a pending row. */
data class ExportResult(val uri: Uri, val width: Int, val height: Int)

/** Re-decode the private source (4096 cap), use FilmRenderer, then publish an sRGB JPEG.
 * Work runs off main. Clean unpublished output on failure/cancellation and propagate
 * CancellationException. Never modify the original or export an enlarged preview.
 */
interface PhotoExporter {
    suspend fun export(request: ExportRequest): ExportResult
}
