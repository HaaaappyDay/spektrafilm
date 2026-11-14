package io.github.haaaappyday.filmeditor.export

import android.content.Context
import android.net.Uri
import android.system.Os
import android.system.OsConstants
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** A journal entry identifies an operation, never grants authority to delete its URI. */
data class PendingExport(val id: String, val displayName: String, val uri: Uri? = null) {
    companion object {
        const val RELATIVE_PATH = "Pictures/FilmEditor/"
        internal val idPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val namePattern = Regex("FilmEditor_\\d{8}_\\d{6}_\\d{3}_(${idPattern.pattern})\\.jpg")
        private val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT).withZone(ZoneOffset.UTC)
        fun create(): PendingExport {
            val id = UUID.randomUUID().toString()
            return PendingExport(id, "FilmEditor_${timestamp.format(Instant.now())}_$id.jpg")
        }
        fun isManagedName(name: String) = namePattern.matches(name)
        internal fun validate(entry: PendingExport) {
            require(idPattern.matches(entry.id))
            require(namePattern.matchEntire(entry.displayName)?.groupValues?.get(1) == entry.id)
            entry.uri?.let {
                val path = it.pathSegments
                require(it.scheme == "content" && it.authority == "media" && it.query == null && it.fragment == null)
                require(path.size == 4 && path[0] in setOf("external", "external_primary") &&
                    path[1] == "images" && path[2] == "media" && path[3].matches(Regex("[0-9]+")))
            }
        }
    }
}

/** Synchronous, bounded IO. Call only on the export IO dispatcher, under coordinator ownership. */
class PendingExportStore(
    context: Context,
    private val directory: File = File(context.applicationContext.noBackupFilesDir, "export-journal"),
    private val commit: (File, File) -> Unit = { pending, committed -> Os.rename(pending.absolutePath, committed.absolutePath) },
) {
    fun write(record: PendingExport) = synchronized(lock) {
        PendingExport.validate(record)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Journal directory unavailable")
        val file = File(directory, "${record.id}.json")
        val pending = File(directory, "${record.id}.json.new")
        val bytes = JSONObject().put("schemaVersion", 1).put("id", record.id)
            .put("displayName", record.displayName).put("uri", record.uri?.toString() ?: JSONObject.NULL)
            .toString().toByteArray(Charsets.UTF_8)
        try {
            FileOutputStream(pending).use { stream -> stream.write(bytes); stream.fd.sync() }
            commit(pending, file)
            if (!file.readBytes().contentEquals(bytes)) throw IOException("Journal commit failed")
            syncDirectory()
        } finally { pending.delete() }
        Unit
    }
    fun read(): List<PendingExport> = synchronized(lock) {
        files().filter { it.name.endsWith(".json") }.mapNotNull { file ->
            try {
                if (file.length() > 64 * 1024) return@mapNotNull null
                val json = JSONObject(file.readText())
                require(json.getInt("schemaVersion") == 1)
                PendingExport(json.getString("id"), json.getString("displayName"),
                    if (json.isNull("uri")) null else Uri.parse(json.getString("uri"))).also {
                    require(file.name == "${it.id}.json"); PendingExport.validate(it)
                }
            } catch (_: Exception) { null } // Recovery still scans verified owned pending rows.
        }
    }
    fun remove(id: String) = synchronized(lock) {
        require(PendingExport.idPattern.matches(id))
        files().filter { it.name == "$id.json" || it.name == "$id.json.new" }.forEach { checkedDelete(it) }
        if (directory.isDirectory) syncDirectory()
    }
    fun clear() = synchronized(lock) {
        files().forEach { checkedDelete(it) }
        if (directory.isDirectory) syncDirectory()
    }
    private fun files(): List<File> {
        if (!directory.exists()) return emptyList()
        val root = directory.canonicalFile
        return (root.listFiles() ?: throw IOException("Cannot enumerate export journal")).filter {
            journalName.matches(it.name) && it.isFile && it.absolutePath == it.canonicalPath && it.parentFile == root
        }
    }
    private fun checkedDelete(file: File) { if (file.exists() && !file.delete()) throw IOException("Cannot remove journal record") }
    private fun syncDirectory() {
        val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
    private companion object {
        val lock = Any()
        val journalName = Regex("${PendingExport.idPattern.pattern}\\.json(?:\\.new)?")
    }
}
