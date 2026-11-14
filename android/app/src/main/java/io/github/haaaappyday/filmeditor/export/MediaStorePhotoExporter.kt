package io.github.haaaappyday.filmeditor.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import io.github.haaaappyday.filmeditor.core.*
import kotlinx.coroutines.*
import java.io.*
import java.security.MessageDigest

/** Owns every decoded/rendered export bitmap. Session owner retains/releases the shared renderer. */
class MediaStorePhotoExporter(
    context: Context,
    private val images: ImageRepository,
    private val renderer: FilmRenderer,
    private val journal: PendingExportStore = PendingExportStore(context),
    private val gateway: MediaStoreGateway = AndroidMediaStoreGateway(context),
    private val stagingDirectory: File = File(context.applicationContext.noBackupFilesDir, "export-staging"),
    private val encode: (Bitmap, File) -> Unit = ::encodeSrgbJpeg,
) : PhotoExporter {
    private val resolver = context.applicationContext.contentResolver
    private val packageName = context.applicationContext.packageName

    /** Invoke through ExportCoordinator, which excludes concurrent export/recovery across instances. */
    override suspend fun export(request: ExportRequest): ExportResult {
        val intent = PendingExport.create()
        var uri: Uri? = null
        var staged: File? = null
        try {
            return withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val input = images.decode(request.source, 4096)
                try {
                    val rendered = renderer.render(input, request.params)
                    try {
                        currentCoroutineContext().ensureActive()
                        check(rendered !== input && rendered.width == input.width && rendered.height == input.height)
                        if (!stagingDirectory.isDirectory && !stagingDirectory.mkdirs()) throw IOException("Export storage unavailable")
                        val file = File(stagingDirectory.canonicalFile, "${intent.id}.jpg")
                        if (!file.createNewFile()) throw IOException("Export staging collision")
                        staged = file
                        encode(rendered, file)
                        currentCoroutineContext().ensureActive()
                        // Record intent before insertion; fallback scanning covers an insert
                        // that creates a row but dies before its URI can be journaled.
                        journal.write(intent)
                        val target = gateway.insert(intent.displayName)
                        uri = target
                        journal.write(intent.copy(uri = target))
                        currentCoroutineContext().ensureActive()
                        val written = MessageDigest.getInstance("SHA-256")
                        gateway.open(target).use { output ->
                            file.inputStream().use { source ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = source.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count); written.update(buffer, 0, count)
                                }
                            }
                            output.flush()
                            if (output is FileOutputStream) output.fd.sync()
                        } // Closing must succeed before publication.
                        currentCoroutineContext().ensureActive()
                        val readBack = resolver.openInputStream(target)?.use { digest(it) }
                            ?: throw IOException("Saved output is unreadable")
                        if (!written.digest().contentEquals(readBack)) throw IOException("Saved output differs from encoded JPEG")
                        gateway.publish(target, intent.displayName)
                        currentCoroutineContext().ensureActive()
                        journal.remove(intent.id)
                        checkedDelete(file)
                        currentCoroutineContext().ensureActive()
                        ExportResult(target, rendered.width, rendered.height)
                    } finally { if (rendered !== input) rendered.recycle() }
                } finally { input.recycle() }
            }
        } catch (error: Throwable) {
            // Includes prompt cancellation returning from IO after publication. This
            // invocation owns its unique operation; startup never deletes published rows.
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    gateway.rows().filter { eligible(it) && it.name == intent.displayName }.forEach { gateway.delete(it, false) }
                    journal.remove(intent.id)
                } catch (cleanup: Exception) {
                    error.addSuppressed(cleanup)
                    try { journal.write(intent.copy(uri = uri)) } catch (recordFailure: Exception) { error.addSuppressed(recordFailure) }
                }
                try { staged?.let(::checkedDelete) } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            }
            throwExportFailure(error)
        }
    }

    /** Never trust journal URIs alone: only provider-verified own, unpublished media is removed. */
    suspend fun recover() = withContext(Dispatchers.IO) {
        try {
            val records = journal.read()
            val rows = gateway.rows()
            val journaled = records.mapNotNull { record -> rows.firstOrNull { it.uri == record.uri && it.name == record.displayName } }
            val orphaned = rows.filter { eligible(it) && it.pending }
            for (row in (journaled + orphaned).distinctBy { it.uri }) {
                currentCoroutineContext().ensureActive()
                if (eligible(row) && row.pending) gateway.delete(row, true)
            }
            val root = stagingDirectory.canonicalFile
            if (root.exists()) {
                val files = root.listFiles() ?: throw IOException("Cannot enumerate export staging")
                files.filter { stagingName.matches(it.name) && it.absolutePath == it.canonicalPath && it.parentFile == root }
                    .forEach { currentCoroutineContext().ensureActive(); checkedDelete(it) }
            }
            journal.clear()
        } catch (error: Throwable) { throwExportFailure(error) }
    }
    private fun eligible(row: MediaRow) = row.owner == packageName && row.path == PendingExport.RELATIVE_PATH && PendingExport.isManagedName(row.name)
    private suspend fun digest(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) return digest.digest()
            digest.update(buffer, 0, count)
        }
    }
    private companion object { val stagingName = Regex("${PendingExport.idPattern.pattern}\\.jpg(?:\\.tmp)?") }
}

internal fun encodeSrgbJpeg(bitmap: Bitmap, file: File) {
    require(bitmap.config == Bitmap.Config.ARGB_8888 && !bitmap.hasAlpha() && bitmap.colorSpace == ColorSpace.get(ColorSpace.Named.SRGB))
    FileOutputStream(file).use {
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) throw IOException("JPEG encoding failed")
        it.fd.sync()
    }
    // Start with encoded pixels, never copied source EXIF/ICC/GPS. Mark their actual space.
    ExifInterface(file).apply {
        setAttribute(ExifInterface.TAG_COLOR_SPACE, "1")
        setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        saveAttributes()
    }
    RandomAccessFile(file, "rw").use { it.fd.sync() }
}
private fun checkedDelete(file: File) { if (file.exists() && !file.delete()) throw IOException("Cannot remove export staging file") }
private fun throwExportFailure(error: Throwable): Nothing {
    if (error is CancellationException) throw error
    if (error is Exception || error is OutOfMemoryError) throw EditorException(EditorErrorCode.EXPORT_FAILED, error)
    throw error
}
