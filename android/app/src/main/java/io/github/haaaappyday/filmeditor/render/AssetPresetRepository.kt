package io.github.haaaappyday.filmeditor.render

import android.content.Context
import io.github.haaaappyday.filmeditor.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest

class AssetPresetRepository(
    context: Context,
    private val openAsset: (String) -> InputStream = context.applicationContext.assets::open,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PresetRepository {
    private val mutex = Mutex()
    private var presets: List<FilmPreset>? = null
    private val cubes = mutableMapOf<String, Cube>()

    override suspend fun listPresets(): List<FilmPreset> = withContext(ioDispatcher) {
        presetFailure { mutex.withLock { loadManifest().toList() } }
    }
    internal suspend fun loadCube(id: String): Cube = withContext(ioDispatcher) {
        presetFailure {
            mutex.withLock {
                cubes[id] ?: run {
                    val preset = loadManifest().singleOrNull { it.id == id } ?: unavailable()
                    val bytes = readBounded(preset.assetPath, 16 * 1024 * 1024)
                    val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    if (sha != preset.sha256) unavailable()
                    val cube = CubeParser.parse(bytes.toString(Charsets.UTF_8))
                    if (cube.size != 33 || !cube.domainMin.contentEquals(floatArrayOf(0f, 0f, 0f)) ||
                        !cube.domainMax.contentEquals(floatArrayOf(1f, 1f, 1f))) unavailable()
                    cubes[id] = cube
                    cube
                }
            }
        }
    }
    private fun loadManifest(): List<FilmPreset> {
        presets?.let { return it }
        val manifest = JSONObject(readBounded("presets/manifest.json", 256 * 1024).toString(Charsets.UTF_8))
        if (manifest.getInt("schemaVersion") != 1) unavailable()
        val entries = manifest.getJSONArray("presets")
        if (entries.length() != 3) unavailable()
        val parsed = (0 until entries.length()).map { index ->
            val item = entries.getJSONObject(index)
            val id = item.getString("id")
            val path = item.getString("assetPath")
            val hash = item.getString("sha256")
            if (id !in expectedIds || path != "presets/$id.cube" || !hash.matches(Regex("[0-9a-f]{64}")) ||
                item.getString("inputColorSpace") != "sRGB" || item.getString("outputColorSpace") != "sRGB" ||
                item.getInt("resolution") != 33 || item.getString("filmProfile") != id ||
                item.getString("printProfile") != "kodak_portra_endura") unavailable()
            for ((key, value) in listOf("domainMin" to 0.0, "domainMax" to 1.0)) {
                val domain = item.getJSONArray(key)
                if (domain.length() != 3 || (0..2).any { domain.getDouble(it) != value }) unavailable()
            }
            val name = item.getString("displayName")
            if (name.isBlank() || name.length > 120) unavailable()
            FilmPreset(id, name, path, hash)
        }
        if (parsed.map { it.id }.toSet() != expectedIds) unavailable()
        presets = parsed
        return parsed
    }
    private fun readBounded(path: String, limit: Int): ByteArray = openAsset(path).use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size().toLong() + count > limit) unavailable()
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    private companion object {
        val expectedIds = setOf("kodak_portra_400", "kodak_gold_200", "kodak_ektar_100")
        fun unavailable(): Nothing = throw EditorException(EditorErrorCode.PRESET_UNAVAILABLE)
    }
}

private suspend inline fun <T> presetFailure(block: () -> T): T = try { block() }
catch (cancelled: CancellationException) { throw cancelled }
catch (error: EditorException) { throw error }
catch (error: Exception) { throw EditorException(EditorErrorCode.PRESET_UNAVAILABLE, error) }
catch (error: OutOfMemoryError) { throw EditorException(EditorErrorCode.PRESET_UNAVAILABLE, error) }
