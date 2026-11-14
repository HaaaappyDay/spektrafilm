package io.github.haaaappyday.filmeditor.e2e

import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.core.EditParams
import io.github.haaaappyday.filmeditor.render.GlFilmRenderer
import io.github.haaaappyday.filmeditor.testsupport.testOutputDirectory
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DeviceEnvironmentTest {
    @Test fun actualGlesDeviceAndBuildMetadataAccompanyValidation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val renderer = GlFilmRenderer(context)
        val input = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff808080.toInt()) }
        try {
            renderer.render(input, EditParams("kodak_portra_400")).recycle()
            val gl = renderer.diagnostics()
            assertTrue(gl.getValue("version"), gl.getValue("version").contains("OpenGL ES 3"))
            assertTrue(gl.getValue("renderer").isNotBlank()); assertTrue(gl.getValue("vendor").isNotBlank())
            val manager = context.getSystemService(ActivityManager::class.java)
            val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
            assertTrue(memory.totalMem > 0)
            val version = context.packageManager.getPackageInfo(context.packageName, 0)
            val args = InstrumentationRegistry.getArguments()
            val sourceCommit = args.getString("sourceCommit", "unrecorded")
            val testedCommit = args.getString("testedCommit", "unrecorded")
            for (commit in listOf(sourceCommit, testedCommit)) {
                if (commit != "unrecorded") assertTrue(commit, commit.matches(Regex("[0-9a-f]{40}")))
            }
            File(testOutputDirectory(), "device-build.json").writeText(JSONObject()
                .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("api", Build.VERSION.SDK_INT).put("release", Build.VERSION.RELEASE)
                .put("fingerprint", Build.FINGERPRINT).put("abi", Build.SUPPORTED_ABIS.first())
                .put("totalRamBytes", memory.totalMem).put("javaHeapLimitBytes", Runtime.getRuntime().maxMemory())
                .put("memoryClassMiB", manager.memoryClass).put("gl", JSONObject(gl))
                .put("package", context.packageName).put("versionName", version.versionName)
                .put("versionCode", version.longVersionCode)
                .put("debuggable", context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
                .put("sourceCommit", sourceCommit).put("testedCommit", testedCommit)
                .put("physicalAcceptance", false).toString(2))
        } finally { renderer.release(); input.recycle() }
    }
}
