package io.github.haaaappyday.filmeditor.render

import io.github.haaaappyday.filmeditor.core.*
import org.junit.Assert.*
import org.junit.Test

class CubeAndMathTest {
    private fun text(header: String = "LUT_3D_SIZE 2\n", swap: Boolean = false): String = header + buildString {
        for (b in 0..1) for (g in 0..1) for (r in 0..1) append(if (swap) "$b $g $r\n" else "$r $g $b\n")
    }
    @Test fun commentsWhitespaceDomainsAndRfastestOrder() {
        val cube = CubeParser.parse(text("# fixture\nTITLE \"identity # title\"\nDOMAIN_MIN\t-1 -1 -1\nDOMAIN_MAX 1 1 1\nLUT_3D_SIZE\t2\n"))
        assertEquals(2, cube.size)
        assertArrayEquals(floatArrayOf(.25f, .5f, .75f), ColorMath.sample(cube, floatArrayOf(-.5f, 0f, .5f)), 1e-6f)
        val swap = CubeParser.parse(text(swap = true))
        assertArrayEquals(floatArrayOf(.9f, .3f, .1f), ColorMath.sample(swap, floatArrayOf(.1f, .3f, .9f)), 1e-6f)
        assertArrayEquals(floatArrayOf(0f, .5f, 1f), ColorMath.sample(swap, floatArrayOf(3f, .5f, -3f)), 1e-6f)
    }
    @Test fun malformedResourcesAreRejected() {
        val invalid = listOf("", text("LUT_1D_SIZE 2\nLUT_3D_SIZE 2\n"), text("LUT_3D_SIZE 1\n"),
            text("LUT_3D_SIZE 66\n"), text()+"0 0 0\n", text().lines().dropLast(2).joinToString("\n"),
            text().replace("0 0 0", "NaN 0 0"), text().replace("0 0 0", "Infinity 0 0"),
            text().replace("0 0 0", "0 0 0 0"), text("DOMAIN_MIN 1 0 0\nDOMAIN_MAX 1 1 1\nLUT_3D_SIZE 2\n"),
            text("LUT_3D_SIZE 2\nLUT_3D_SIZE 2\n"), text()+"DOMAIN_MIN 0 0 0\n")
        for (source in invalid) {
            try { CubeParser.parse(source); fail("Accepted malformed cube: ${source.take(80)}") }
            catch (expected: EditorException) { assertEquals(EditorErrorCode.PRESET_UNAVAILABLE, expected.code) }
        }
    }
    @Test fun identityAndStrengthBypassUseLinearExposureAndWarmGain() {
        val cube = CubeParser.parse(text())
        val rgb = floatArrayOf(.2f, .3f, .4f)
        for (strength in listOf(0f, .5f, 1f)) assertArrayEquals(rgb,
            ColorMath.transform(rgb, cube, EditParams("identity", strength)), 2e-6f)
        val black = CubeParser.parse("LUT_3D_SIZE 2\n" + "0 0 0\n".repeat(8))
        val ev = ColorMath.transform(rgb, black, EditParams("black", 0f, 1f, 0f))
        for (c in 0..2) assertEquals(ColorMath.decode(rgb[c])*2, ColorMath.decode(ev[c]), 1e-6f)
        val warm = ColorMath.transform(rgb, black, EditParams("black", 0f, 0f, 100f))
        assertTrue(warm[0] > rgb[0]); assertEquals(rgb[1], warm[1], 1e-6f); assertTrue(warm[2] < rgb[2])
    }
    @Test fun invalidParametersAreRejected() {
        val cases = listOf(EditParams("p", Float.NaN), EditParams("p", 1.01f), EditParams("p", -.1f),
            EditParams("p", exposureEv = Float.POSITIVE_INFINITY), EditParams("p", exposureEv = -2.1f),
            EditParams("p", temperature = 101f), EditParams("p", temperature = Float.NaN))
        for (params in cases) {
            try { ColorMath.validate(params); fail("Accepted $params") }
            catch (expected: EditorException) { assertEquals(EditorErrorCode.INVALID_PARAMS, expected.code) }
        }
    }
}
