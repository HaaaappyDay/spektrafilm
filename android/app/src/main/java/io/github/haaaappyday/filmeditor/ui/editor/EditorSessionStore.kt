package io.github.haaaappyday.filmeditor.ui.editor

import android.content.Context
import android.util.AtomicFile
import io.github.haaaappyday.filmeditor.core.*
import io.github.haaaappyday.filmeditor.image.ImageSizing
import io.github.haaaappyday.filmeditor.render.ColorMath
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** Durable state contains descriptors and values only; export success is intentionally transient. */
data class StoredEdit(val source: ImageSource, val params: EditParams)
interface SessionPersistence {
    suspend fun load(): StoredEdit?
    suspend fun save(edit: StoredEdit?)
}

class EditorSessionStore(
    context: Context,
    private val directory: File = File(context.applicationContext.noBackupFilesDir, "editor-session"),
    private val sourcesDirectory: File = File(context.applicationContext.noBackupFilesDir, "sources"),
    private val finishWrite: (AtomicFile, FileOutputStream) -> Unit = { atomic, stream -> atomic.finishWrite(stream) },
) : SessionPersistence {
    private val file = AtomicFile(File(directory, "editor.json"))

    override suspend fun load(): StoredEdit? = withContext(Dispatchers.IO) {
        storageFailure {
            storageMutex.withLock {
                // Claim before parsing, including corrupt state: a later Activity in this
                // process must never prune candidates belonging to an already active editor.
                val first = initializedRoots.add(directory.canonicalPath)
                val edit = if (!file.baseFile.exists() && !File(directory, "editor.json.bak").exists()) null else {
                    val bytes = file.openRead().use { input ->
                        val output = ByteArrayOutputStream(); val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > 64 * 1024) throw IllegalArgumentException("Session too large")
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    decode(JSONObject(bytes.toString(Charsets.UTF_8))).also { validate(it) }
                }
                if (first) pruneOrphans(edit?.source)
                edit
            }
        }
    }
    override suspend fun save(edit: StoredEdit?) = withContext(Dispatchers.IO) {
        storageFailure {
            storageMutex.withLock {
                if (edit == null) { file.delete(); return@withLock }
                validate(edit)
                check(directory.isDirectory || directory.mkdirs())
                val bytes = encode(edit).toString().toByteArray(Charsets.UTF_8)
                val stream = file.startWrite()
                try { stream.write(bytes); finishWrite(file, stream) }
                catch (error: Throwable) { file.failWrite(stream); throw error }
            }
        }
    }
    private fun validate(edit: StoredEdit) {
        val source = edit.source
        val actual = File(source.localPath)
        require(uuid.matches(source.id) && actual.name == "${source.id}.source")
        require(actual.isAbsolute && actual.absolutePath == actual.canonicalPath && actual.parentFile == sourcesDirectory.canonicalFile)
        require(source.mimeType in setOf("image/jpeg", "image/png"))
        ImageSizing.fit(source.width, source.height, 4096)
        ColorMath.validate(edit.params)
        require(edit.params.presetId.matches(Regex("[a-z0-9_]{1,80}")))
    }
    private fun pruneOrphans(keep: ImageSource?) {
        val root = sourcesDirectory.canonicalFile
        root.listFiles()?.filter { sourcePattern.matches(it.name) && it.absolutePath == it.canonicalPath && it.parentFile == root && it.absolutePath != keep?.localPath }
            ?.forEach { it.delete() }
    }
    private fun encode(edit: StoredEdit): JSONObject = JSONObject().put("schemaVersion", 1)
        .put("source", JSONObject().put("id", edit.source.id).put("localPath", edit.source.localPath)
            .put("width", edit.source.width).put("height", edit.source.height).put("mimeType", edit.source.mimeType))
        .put("params", JSONObject().put("presetId", edit.params.presetId).put("strength", edit.params.strength.toDouble())
            .put("exposureEv", edit.params.exposureEv.toDouble()).put("temperature", edit.params.temperature.toDouble()))
    private fun decode(json: JSONObject): StoredEdit {
        require(json.getInt("schemaVersion") == 1)
        val source = json.getJSONObject("source"); val params = json.getJSONObject("params")
        return StoredEdit(ImageSource(source.getString("id"), source.getString("localPath"), source.getInt("width"), source.getInt("height"), source.getString("mimeType")),
            EditParams(params.getString("presetId"), params.getDouble("strength").toFloat(), params.getDouble("exposureEv").toFloat(), params.getDouble("temperature").toFloat()))
    }
    private companion object {
        val storageMutex = Mutex()
        val initializedRoots = mutableSetOf<String>()
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val sourcePattern = Regex("${uuid.pattern}\\.source")
    }
}

private suspend inline fun <T> storageFailure(block: () -> T): T = try { block() }
catch (cancelled: CancellationException) { throw cancelled }
catch (error: Exception) { throw EditorException(EditorErrorCode.READ_FAILED, error) }
