package io.github.haaaappyday.filmeditor.testsupport

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry

/** The fixture provider belongs to this variant's test APK. */
fun fixtureUri(name: String): Uri = Uri.Builder()
    .scheme("content")
    .authority("${InstrumentationRegistry.getInstrumentation().context.packageName}.fixtures")
    .appendPath(name)
    .build()
