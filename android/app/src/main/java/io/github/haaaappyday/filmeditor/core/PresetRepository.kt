package io.github.haaaappyday.filmeditor.core

/** Disk work runs off main; malformed/missing assets yield PRESET_UNAVAILABLE.
 * CancellationException propagates unchanged.
 */
interface PresetRepository {
    suspend fun listPresets(): List<FilmPreset>
}
