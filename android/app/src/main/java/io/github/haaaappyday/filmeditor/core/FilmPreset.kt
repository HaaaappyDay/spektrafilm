package io.github.haaaappyday.filmeditor.core

/** assetPath is relative to assets; sha256 authenticates the packaged cube bytes. */
data class FilmPreset(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val sha256: String,
)
