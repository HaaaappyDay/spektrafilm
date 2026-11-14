package io.github.haaaappyday.filmeditor.image

import io.github.haaaappyday.filmeditor.core.EditorErrorCode
import io.github.haaaappyday.filmeditor.core.EditorException
import kotlin.math.max
import kotlin.math.roundToInt

internal object ImageSizing {
    fun fit(width: Int, height: Int, maxLongEdge: Int): Pair<Int, Int> {
        if (maxLongEdge !in 1..4096) throw EditorException(EditorErrorCode.INVALID_PARAMS)
        if (width !in 1..32768 || height !in 1..32768 || width.toLong() * height > 100_000_000L) {
            throw EditorException(EditorErrorCode.IMAGE_TOO_LARGE)
        }
        val longEdge = max(width, height)
        if (longEdge <= maxLongEdge) return width to height
        val scale = maxLongEdge.toDouble() / longEdge
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }
}
