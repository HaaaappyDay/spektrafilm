package io.github.haaaappyday.filmeditor.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import io.github.haaaappyday.filmeditor.core.*
import kotlinx.coroutines.*
import java.io.*
import java.util.UUID

/** Sources are byte-for-byte private copies, excluded from Android backup/device transfer. */
class AndroidImageRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val directory: () -> File = { File(context.applicationContext.noBackupFilesDir, "sources") },
    private val openInput: (Uri) -> InputStream? = context.applicationContext.contentResolver::openInputStream,
) : ImageRepository {
    private val normalizer = ImageNormalizer()
    private var initialized = false // only accessed under storageLock

    override suspend fun importImage(uri: Uri): ImageSource {
        var finalized: File? = null
        try {
            return withContext(ioDispatcher) {
                translateReadFailure {
                    val root = storage()
                    val id = UUID.randomUUID().toString()
                    val part = File(root, "$id.part")
                    val destination = File(root, "$id.source")
                    synchronized(storageLock) {
                        check(activeParts.add(part.absolutePath))
                    }
                    try {
                        if (!part.createNewFile()) throw IOException("Source collision")
                        (openInput(uri) ?: throw IOException("Source unavailable")).use { input ->
                            FileOutputStream(part).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var total = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (read == 0) continue
                                    total += read
                                    if (total > ImageNormalizer.MAX_SOURCE_BYTES) throw EditorException(EditorErrorCode.IMAGE_TOO_LARGE)
                                    output.write(buffer, 0, read)
                                }
                                output.fd.sync()
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        val info = normalizer.inspect(part)
                        currentCoroutineContext().ensureActive()
                        if (destination.exists() || !part.renameTo(destination)) throw IOException("Cannot finalize source")
                        finalized = destination
                        ImageSource(id, destination.absolutePath, info.width, info.height, info.mimeType)
                    } finally {
                        synchronized(storageLock) {
                            part.delete()
                            activeParts.remove(part.absolutePath)
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            // Includes prompt cancellation while returning from IO: the caller never owned
            // the descriptor, so even an already-finalized source must be removed.
            withContext(NonCancellable + ioDispatcher) { finalized?.delete() }
            throw error
        }
    }

    override suspend fun decode(source: ImageSource, maxLongEdge: Int): Bitmap {
        var produced: Bitmap? = null
        try {
            return withContext(ioDispatcher) {
                translateReadFailure {
                    currentCoroutineContext().ensureActive()
                    normalizer.decode(managedPath(source), maxLongEdge).also { produced = it }
                }
            }
        } catch (error: Throwable) {
            produced?.recycle() // never delivered/displayed
            throw error
        }
    }

    override suspend fun remove(source: ImageSource) = withContext(ioDispatcher) {
        translateReadFailure {
            val file = managedPath(source)
            if (file.exists() && !file.delete()) throw IOException("Cannot remove unused source")
        }
    }

    private fun storage(): File = synchronized(storageLock) {
        val root = directory().canonicalFile
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Private storage unavailable")
        if (!initialized) {
            root.listFiles()?.filter { partPattern.matches(it.name) && it.absolutePath !in activeParts }
                ?.forEach { it.delete() }
            initialized = true
        }
        root
    }

    private fun managedPath(source: ImageSource): File {
        val root = storage()
        val file = File(source.localPath)
        if (!idPattern.matches(source.id) || file.name != "${source.id}.source" ||
            file.absolutePath != file.canonicalPath || file.parentFile != root) {
            throw EditorException(EditorErrorCode.READ_FAILED)
        }
        return file
    }

    companion object {
        private val storageLock = Any()
        private val activeParts = mutableSetOf<String>()
        private val idPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val partPattern = Regex("${idPattern.pattern}\\.part")
    }
}
