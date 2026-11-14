package io.github.haaaappyday.filmeditor.testsupport

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** AGP collects this directory before uninstalling the test application. */
fun testOutputDirectory(): File {
    val supplied = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Suppress("DEPRECATION")
    return (supplied?.let(::File) ?: File(context.externalMediaDirs.first(), "additional_test_output"))
        .apply { check(isDirectory || mkdirs()) }
}
