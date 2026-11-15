package io.github.haaaappyday.filmeditor.core

/** Immutable edit snapshot. Services reject non-finite/out-of-range values with INVALID_PARAMS.
 * Strength [0,1], exposure [-2,+2] EV. Temperature, tint, contrast, highlights, shadows
 * and saturation are [-100,+100]. Positive temperature is warmer and positive tint is
 * magenta; neither is Kelvin. Unknown preset IDs yield PRESET_UNAVAILABLE.
 */
data class EditParams(
    val presetId: String,
    val strength: Float = 1f,
    val exposureEv: Float = 0f,
    val temperature: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val saturation: Float = 0f,
    val tint: Float = 0f,
)
