plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.haaaappyday.filmeditor"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.github.haaaappyday.filmeditor"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        val sourceCommit = providers.gradleProperty("sourceCommit").getOrElse("unrecorded")
        require(sourceCommit == "unrecorded" || sourceCommit.matches(Regex("[0-9a-f]{40}")))
        buildConfigField("String", "SOURCE_COMMIT", "\"$sourceCommit\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notClass"] = "io.github.haaaappyday.filmeditor.e2e.ProcessRestorePhases,io.github.haaaappyday.filmeditor.e2e.PerformanceScenario"
        manifestPlaceholders["fixtureAuthority"] = "$applicationId.test.fixtures"
    }
    // Instrumented tests own their generated sources and recovery state. Never
    // run session-clearing tests against the app installed for everyday use.
    buildTypes {
        create("validation") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".validation"
            matchingFallbacks += "debug"
            manifestPlaceholders["fixtureAuthority"] = "io.github.haaaappyday.filmeditor.validation.test.fixtures"
        }
        create("profileable") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".validation"
            matchingFallbacks += "release"
            manifestPlaceholders["fixtureAuthority"] = "io.github.haaaappyday.filmeditor.validation.test.fixtures"
        }
    }
    testBuildType = providers.gradleProperty("validationBuildType").getOrElse("validation").also {
        require(it in setOf("validation", "profileable")) { "Tests require an isolated validation build" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    lint { abortOnError = true }
}
kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    "validationImplementation"(libs.compose.ui.test.manifest)
    "profileableImplementation"(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose's transitive Espresso 3.5.0 reflects an InputManager API removed
    // on API37. Pin the stable public-service implementation for all runtimes.
    androidTestImplementation(libs.androidx.test.espresso.core)
}
