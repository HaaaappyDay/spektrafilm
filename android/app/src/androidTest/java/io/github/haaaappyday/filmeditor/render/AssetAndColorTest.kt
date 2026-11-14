package io.github.haaaappyday.filmeditor.render

import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.core.*
import io.github.haaaappyday.filmeditor.testsupport.testOutputDirectory
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class AssetAndColorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @Test fun everyShippedPresetAndAllParameterCombinationsMatchIndependentPython() = runBlocking {
        val repository = AssetPresetRepository(context)
        assertEquals(setOf("kodak_portra_400", "kodak_gold_200", "kodak_ektar_100"), repository.listPresets().map { it.id }.toSet())
        val fixture = instrumentation.context.assets
        val metadata = JSONObject(fixture.open("render-fixtures/reference.json").bufferedReader().use { it.readText() })
        val input = fixture.open("render-fixtures/input.rgba").use { it.readBytes() }
        val cases = metadata.getJSONArray("cases")
        val errors = mutableListOf<Int>()
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val cube = repository.loadCube(case.getString("presetId"))
            val params = EditParams(case.getString("presetId"), case.getDouble("strength").toFloat(), case.getDouble("exposureEv").toFloat(), case.getDouble("temperature").toFloat())
            val expected = fixture.open("render-fixtures/" + case.getString("expected")).use { it.readBytes() }
            assertEquals(input.size, expected.size)
            for (offset in input.indices step 4) {
                val rgb = FloatArray(3) { (input[offset + it].toInt() and 255) / 255f }
                val actual = ColorMath.transform(rgb, cube, params)
                for (c in 0..2) {
                    val error = abs((actual[c] * 255).roundToInt() - (expected[offset + c].toInt() and 255))
                    errors.add(error)
                    assertTrue("case=$i pixel=${offset/4} channel=$c error=$error", error <= 2)
                }
            }
        }
        errors.sort()
        File(testOutputDirectory(), "cpu-color-error.json").writeText(JSONObject()
            .put("channels", errors.size).put("mean", errors.average()).put("p95", errors[(errors.size * .95).toInt()]).put("max", errors.last()).toString())
    }

    @Test fun missingCorruptOrUnknownPresetsNeverFallBack() = runBlocking {
        val repository = AssetPresetRepository(context)
        try { repository.loadCube("unknown"); fail("Unknown preset") }
        catch (error: EditorException) { assertEquals(EditorErrorCode.PRESET_UNAVAILABLE, error.code) }
        for (mutation in listOf("missing", "checksum", "schema", "path")) {
            val broken = AssetPresetRepository(context, openAsset = { path ->
                if (path.endsWith(".cube")) {
                    if (mutation == "missing") throw java.io.FileNotFoundException(path)
                    if (mutation == "checksum") return@AssetPresetRepository ByteArrayInputStream("invalid cube".toByteArray())
                }
                if (path == "presets/manifest.json" && mutation in listOf("schema", "path")) {
                    val json = JSONObject(context.assets.open(path).bufferedReader().use { it.readText() })
                    if (mutation == "schema") json.put("schemaVersion", 99)
                    else json.getJSONArray("presets").getJSONObject(0).put("assetPath", "presets/../secret.cube")
                    ByteArrayInputStream(json.toString().toByteArray())
                } else context.assets.open(path)
            })
            try { broken.loadCube("kodak_portra_400"); fail("Accepted $mutation") }
            catch (error: EditorException) { assertEquals(EditorErrorCode.PRESET_UNAVAILABLE, error.code) }
        }
    }
}
