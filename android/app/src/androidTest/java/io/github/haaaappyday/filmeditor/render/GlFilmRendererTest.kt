package io.github.haaaappyday.filmeditor.render

import android.graphics.Bitmap
import android.graphics.ColorSpace
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.core.*
import io.github.haaaappyday.filmeditor.testsupport.testOutputDirectory
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs

class GlFilmRendererTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val assets = instrumentation.context.assets
    private fun input(): Bitmap {
        val meta = JSONObject(assets.open("render-fixtures/reference.json").bufferedReader().use { it.readText() })
        val bytes = assets.open("render-fixtures/input.rgba").use { it.readBytes() }
        val pixels = IntArray(bytes.size / 4) { i ->
            (255 shl 24) or ((bytes[i*4].toInt() and 255) shl 16) or ((bytes[i*4+1].toInt() and 255) shl 8) or (bytes[i*4+2].toInt() and 255)
        }
        return Bitmap.createBitmap(pixels, meta.getInt("width"), meta.getInt("height"), Bitmap.Config.ARGB_8888)
    }
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    private fun identity(domain: Int = 1): Cube = CubeParser.parse("DOMAIN_MAX $domain $domain $domain\nLUT_3D_SIZE 2\n" + buildString {
        for (b in 0..1) for (g in 0..1) for (r in 0..1) append("$r $g $b\n")
    })

    @Test fun actualGpuMatchesEveryIndependentPythonFixture() = runBlocking {
        val renderer = GlFilmRenderer(context)
        val source = input()
        val before = pixels(source)
        val cases = JSONObject(assets.open("render-fixtures/reference.json").bufferedReader().use { it.readText() }).getJSONArray("cases")
        val errors = mutableListOf<Int>()
        try {
            for (i in 0 until cases.length()) {
                val case = cases.getJSONObject(i)
                val params = EditParams(case.getString("presetId"), case.getDouble("strength").toFloat(), case.getDouble("exposureEv").toFloat(), case.getDouble("temperature").toFloat())
                val expected = assets.open("render-fixtures/" + case.getString("expected")).use { it.readBytes() }
                val output = renderer.render(source, params)
                try {
                    assertNotSame(source, output)
                    assertEquals(source.width, output.width); assertEquals(source.height, output.height)
                    assertEquals(Bitmap.Config.ARGB_8888, output.config)
                    assertEquals(ColorSpace.get(ColorSpace.Named.SRGB), output.colorSpace)
                    val actual = pixels(output)
                    for (p in actual.indices) {
                        assertEquals(255, actual[p] ushr 24)
                        for (c in 0..2) {
                            val error = abs(((actual[p] ushr (16-8*c)) and 255) - (expected[p*4+c].toInt() and 255))
                            errors.add(error)
                            assertTrue("case=$i pixel=$p channel=$c error=$error", error <= 2)
                        }
                    }
                    if (case.getDouble("strength") == 1.0 && case.getDouble("exposureEv") == 0.0 && case.getDouble("temperature") == 0.0) {
                        File(testOutputDirectory(), params.presetId + "-gpu.png").outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                } finally { output.recycle() }
            }
            assertArrayEquals(before, pixels(source))
            errors.sort()
            File(testOutputDirectory(), "gpu-color-error.json").writeText(JSONObject()
                .put("channels", errors.size).put("mean", errors.average()).put("p95", errors[(errors.size*.95).toInt()]).put("max", errors.last())
                .put("device", android.os.Build.MODEL).put("api", android.os.Build.VERSION.SDK_INT)
                .put("gl", JSONObject(renderer.diagnostics())).toString())
        } finally { renderer.release(); source.recycle() }
    }

    @Test fun tilingIdentityDomainsAndContextRecreationPreservePixels() = runBlocking {
        val source = input()
        val whole = GlFilmRenderer(context)
        val tiled = GlFilmRenderer(context, tileEdge = 17)
        val identity = GlFilmRenderer(context, tileEdge = 17, cubeProvider = { identity() })
        val domain = GlFilmRenderer(context, tileEdge = 17, cubeProvider = { identity(2) })
        try {
            val params = EditParams("kodak_portra_400", .5f, .7f, 43f)
            val a = whole.render(source, params); val b = tiled.render(source, params)
            try { assertArrayEquals(pixels(a), pixels(b)) } finally { a.recycle(); b.recycle() }
            for (strength in listOf(0f, 1f)) {
                val output = identity.render(source, EditParams("identity", strength))
                try { assertArrayEquals(pixels(source), pixels(output)) } finally { output.recycle() }
            }
            val half = domain.render(source, EditParams("domain"))
            try {
                val original = pixels(source); val actual = pixels(half)
                for (i in original.indices) for (shift in listOf(0, 8, 16)) {
                    assertTrue(abs(((actual[i] ushr shift) and 255) - ((original[i] ushr shift) and 255)/2.0) <= 1)
                }
            } finally { half.recycle() }
            whole.resetContextForTest()
            val fresh = whole.render(source, params); val same = tiled.render(source, params)
            try { assertArrayEquals(pixels(same), pixels(fresh)) } finally { fresh.recycle(); same.recycle() }
        } finally { whole.release(); tiled.release(); identity.release(); domain.release(); source.recycle() }
    }

    @Test fun cancellationQueuedReleaseAndRepeatedReleaseRespectOwnership() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val continueLoad = CompletableDeferred<Unit>()
        val renderer = GlFilmRenderer(context, tileEdge = 17, cubeProvider = {
            entered.complete(Unit)
            continueLoad.await()
            identity()
        })
        val source = input()
        try {
            val obsolete = async { renderer.render(source, EditParams("identity")) }
            entered.await()
            obsolete.cancelAndJoin()
            assertFalse(source.isRecycled)
            continueLoad.complete(Unit)
            val first = async { renderer.render(source, EditParams("identity")) }
            val second = async { renderer.render(source, EditParams("identity")) }
            val a = first.await(); val b = second.await()
            try { assertArrayEquals(pixels(a), pixels(b)) } finally { a.recycle(); b.recycle() }
            renderer.release(); renderer.release()
            try { renderer.render(source, EditParams("identity")); fail("render after release") }
            catch (error: EditorException) { assertEquals(EditorErrorCode.RENDER_FAILED, error.code) }
            assertFalse(source.isRecycled)
        } finally { renderer.release(); source.recycle() }
    }
}
