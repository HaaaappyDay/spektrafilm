package io.github.haaaappyday.filmeditor.fakes

import android.graphics.Bitmap
import android.net.Uri
import io.github.haaaappyday.filmeditor.core.*

/** Test-only callbacks make success, failure and cancellation independently injectable. */
class FakeImageRepository(
    var onImport: suspend (Uri) -> ImageSource,
    var onDecode: suspend (ImageSource, Int) -> Bitmap,
    var onRemove: suspend (ImageSource) -> Unit = {},
) : ImageRepository {
    override suspend fun importImage(uri: Uri) = onImport(uri)
    override suspend fun decode(source: ImageSource, maxLongEdge: Int) = onDecode(source, maxLongEdge)
    override suspend fun remove(source: ImageSource) = onRemove(source)
}

class FakePresetRepository(var presets: List<FilmPreset>) : PresetRepository {
    override suspend fun listPresets() = presets
}

class FakeFilmRenderer(var onRender: suspend (Bitmap, EditParams) -> Bitmap) : FilmRenderer {
    var releaseCalls = 0
        private set
    override suspend fun render(input: Bitmap, params: EditParams) = onRender(input, params)
    override suspend fun release() { releaseCalls++ }
}

class FakePhotoExporter(var onExport: suspend (ExportRequest) -> ExportResult) : PhotoExporter {
    override suspend fun export(request: ExportRequest) = onExport(request)
}
