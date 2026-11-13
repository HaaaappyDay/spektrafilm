package io.github.haaaappyday.filmeditor.core

/** Immutable edit snapshot. Services reject non-finite/out-of-range values with INVALID_PARAMS.
 * Strength [0,1], exposure [-2,+2] EV, temperature [-100,+100] (positive is warmer,
 * not Kelvin). Unknown preset IDs yield PRESET_UNAVAILABLE; never silently replace one.
 */
data class EditParams(
    val presetId: String,
    val strength: Float = 1f,
    val exposureEv: Float = 0f,
    val temperature: Float = 0f,
)
