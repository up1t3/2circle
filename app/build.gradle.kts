plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.twocircle.bike"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.twocircle.bike"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.8.5"

        // v1 UI locales. Default (values/) is English; ru and es ship explicitly.
        // resConfigs also strips unused locales bundled by transitive deps (Hilt, Room,
        // Compose) so the APK doesn't carry dozens of languages we don't translate.
        resourceConfigurations += listOf("en", "ru", "es")

        // Instrumentation tests need a Hilt-aware test runner to inject the
        // @HiltAndroidTest-annotated Application.
        testInstrumentationRunner = "com.twocircle.bike.HiltTestRunner"

        buildConfigField(
            "String",
            "MANIFEST_URL",
            "\"http://72.56.238.106:8765/manifest.json\"",
        )
    }

    signingConfigs {
        // Temporary release signing via the debug keystore. Lets us ship a signed APK
        // now (the project has no dedicated release keystore yet); installs update
        // in-place over debug builds. Swap for a real release key before Play Store.
        // ~/.android/debug.keystore: alias=androiddebugkey, store/key password=android.
        create("releaseFromDebug") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseFromDebug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))

    implementation(project(":feature:map"))
    implementation(project(":feature:search"))
    implementation(project(":feature:routing"))
    implementation(project(":feature:routing-brouter"))
    implementation(project(":feature:tracking"))
    implementation(project(":feature:tracks"))
    implementation(project(":feature:regions"))
    implementation(project(":feature:poi"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:social"))

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)

    // Logging
    implementation(libs.timber)

    testImplementation(libs.junit)

    // Instrumentation (androidTest) — runs on device via connectedAndroidTest.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)
    // Hilt testing: add the testing artifact + its KSP processor for test-only modules.
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
