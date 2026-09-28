import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Screenshot tests. The plugin itself is inert until a test asks for a capture, so declaring
    // it costs nothing on a normal build.
    alias(libs.plugins.roborazzi)
}

// Release signing is driven by an untracked keystore.properties so credentials
// never enter git. Absent that file `assembleRelease` still produces an unsigned
// APK rather than failing.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.lingodeck.reader"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.lingodeck.reader"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v1 is redundant above API 23 and v2 covers minSdk 26 on its own, but v3 is
                // what makes a later key rotation possible without reinstalling, so it is
                // requested explicitly rather than left to whatever AGP defaults to.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // No R8: the app is four thousand lines with two runtime
            // dependencies, so shrinking buys very little and a misconfigured
            // keep rule would quietly break the Anki ContentProvider contract.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // For VERSION_NAME, so Settings shows the real version rather than a copy of it that
        // can drift from the manifest.
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric has to resolve real resources to lay a Compose screen out, and the
        // hardware renderer is what makes text render rather than coming out as boxes.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            it.systemProperty("roborazzi.test.record", System.getProperty("roborazzi.test.record") ?: "false")
        }
    }

    lint {
        // Lint has never been run on this project, and when it finally was, it surfaced one real
        // gap (a haptics setting with no UI behind it) and one wrong answer. The version checks
        // are silenced on purpose: every version in the build is pinned for a stated reason in
        // gradle/libs.versions.toml, and "something newer exists" is not information worth
        // failing a build over. The reasoning lives in the catalog, not here.
        //
        // The two accepted findings that are scoped to a single file are in app/lint.xml, because
        // the Kotlin DSL has no path-scoped ignore.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    // Compose versions come from the BOM; material3 deliberately does not,
    // see the note on the material3 version in gradle/libs.versions.toml.
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.jsoup)
    implementation(libs.coroutines.android)

    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.compose.ui.test.manifest)
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}
