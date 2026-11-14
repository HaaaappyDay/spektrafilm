package io.github.haaaappyday.filmeditor.render

import io.github.haaaappyday.filmeditor.core.*

/** R-fastest storage: ((b * size + g) * size + r) * 3 + channel. */
internal data class Cube(val size: Int, val domainMin: FloatArray, val domainMax: FloatArray, val values: FloatArray)

internal object CubeParser {
    fun parse(text: String): Cube {
        fun invalid(): Nothing = throw EditorException(EditorErrorCode.PRESET_UNAVAILABLE)
        if (text.length > 32 * 1024 * 1024) invalid()
        var size = 0
        var values: FloatArray? = null
        var count = 0
        var body = false
        var minimum = floatArrayOf(0f, 0f, 0f)
        var maximum = floatArrayOf(1f, 1f, 1f)
        val headers = mutableSetOf<String>()
        fun triplet(parts: List<String>): FloatArray {
            if (parts.size != 3) invalid()
            return FloatArray(3) { index -> parts[index].toFloatOrNull()?.takeIf { it.isFinite() } ?: invalid() }
        }
        for (raw in text.lineSequence()) {
            var quoted = false
            var end = raw.length
            for (i in raw.indices) {
                if (raw[i] == '"') quoted = !quoted
                if (raw[i] == '#' && !quoted) { end = i; break }
            }
            val line = raw.substring(0, end).trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            val name = parts[0].uppercase()
            if (name in setOf("TITLE", "DOMAIN_MIN", "DOMAIN_MAX", "LUT_3D_SIZE")) {
                if (body || !headers.add(name)) invalid()
                when (name) {
                    "TITLE" -> if (!line.substringAfter(parts[0]).trim().matches(Regex("\"[^\"]*\""))) invalid()
                    "DOMAIN_MIN" -> minimum = triplet(parts.drop(1))
                    "DOMAIN_MAX" -> maximum = triplet(parts.drop(1))
                    "LUT_3D_SIZE" -> {
                        if (parts.size != 2) invalid()
                        size = parts[1].toIntOrNull()?.takeIf { it in 2..65 } ?: invalid()
                        values = FloatArray(size * size * size * 3)
                    }
                }
            } else {
                body = true
                val target = values ?: invalid()
                if (count + 3 > target.size) invalid()
                val rgb = triplet(parts)
                rgb.copyInto(target, count)
                count += 3
            }
        }
        val table = values ?: invalid()
        if (count != table.size || (0..2).any { maximum[it] <= minimum[it] }) invalid()
        return Cube(size, minimum, maximum, table)
    }
}
