package io.github.haaaappyday.filmeditor.render

import io.github.haaaappyday.filmeditor.core.*
import kotlin.math.floor
import kotlin.math.pow

/** CPU reference for the shader; all working values are encoded or linear sRGB as named. */
internal object ColorMath {
    fun validate(params: EditParams) {
        if (!params.strength.isFinite() || params.strength !in 0f..1f ||
            !params.exposureEv.isFinite() || params.exposureEv !in -2f..2f ||
            !params.temperature.isFinite() || params.temperature !in -100f..100f) {
            throw EditorException(EditorErrorCode.INVALID_PARAMS)
        }
    }
    fun decode(encoded: Float): Float = if (encoded <= .04045f) encoded / 12.92f else ((encoded + .055f) / 1.055f).pow(2.4f)
    fun encode(linear: Float): Float = if (linear <= .0031308f) 12.92f * linear else 1.055f * linear.pow(1f / 2.4f) - .055f
    fun sample(cube: Cube, rgb: FloatArray): FloatArray {
        val position = FloatArray(3) { ((rgb[it] - cube.domainMin[it]) / (cube.domainMax[it] - cube.domainMin[it])).coerceIn(0f, 1f) * (cube.size - 1) }
        val lo = IntArray(3) { floor(position[it]).toInt() }
        val hi = IntArray(3) { minOf(lo[it] + 1, cube.size - 1) }
        val fraction = FloatArray(3) { position[it] - lo[it] }
        val output = FloatArray(3)
        for (b in 0..1) for (g in 0..1) for (r in 0..1) {
            val ri = if (r == 0) lo[0] else hi[0]
            val gi = if (g == 0) lo[1] else hi[1]
            val bi = if (b == 0) lo[2] else hi[2]
            val weight = (if (r == 0) 1 - fraction[0] else fraction[0]) *
                (if (g == 0) 1 - fraction[1] else fraction[1]) * (if (b == 0) 1 - fraction[2] else fraction[2])
            val offset = ((bi * cube.size + gi) * cube.size + ri) * 3
            for (c in 0..2) output[c] += weight * cube.values[offset + c]
        }
        return output
    }
    fun transform(rgb: FloatArray, cube: Cube, params: EditParams): FloatArray {
        validate(params)
        val t = params.temperature / 100f
        val gains = floatArrayOf(2f.pow(.25f*t), 1f, 2f.pow(-.25f*t))
        val exposure = 2f.pow(params.exposureEv)
        val corrected = FloatArray(3) { (decode(rgb[it]) * gains[it] * exposure).coerceIn(0f, 1f) }
        val film = sample(cube, FloatArray(3) { encode(corrected[it]) })
        return FloatArray(3) {
            encode((1 - params.strength) * corrected[it] + params.strength * decode(film[it].coerceIn(0f, 1f))).coerceIn(0f, 1f)
        }
    }
}
