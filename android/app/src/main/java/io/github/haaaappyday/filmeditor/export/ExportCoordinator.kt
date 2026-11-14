package io.github.haaaappyday.filmeditor.export

import io.github.haaaappyday.filmeditor.core.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One app-wide export or recovery operation; repeated Save never becomes a queued export. */
class ExportCoordinator(
    private val delegate: PhotoExporter,
    private val recoverAction: suspend () -> Unit,
) : PhotoExporter {
    override suspend fun export(request: ExportRequest): ExportResult {
        if (!gate.tryLock()) throw EditorException(EditorErrorCode.EXPORT_FAILED)
        try {
            // Retry must first recover a previous operation whose cleanup failed.
            // Startup recovery alone is insufficient while this process stays alive.
            recoverAction()
            return delegate.export(request)
        } finally { gate.unlock() }
    }
    suspend fun recover() = gate.withLock { recoverAction() }
    private companion object { val gate = Mutex() }
}
