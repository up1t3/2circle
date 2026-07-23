plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.twocircle.bike.feature.map"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    // org.json from android.jar is stubbed in unit tests — return real values so the
    // style-JSON parser tests run without Robolectric.
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric needs access to merged assets/resources at test time.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    api(project(":core:designsystem"))
    implementation(project(":core:data"))
    // Poi model for the POI marker layer rendered inside BikeMap.
    api(project(":feature:poi"))

    // MapLibre Native — offline vector tiles + runtime styling.
    api(libs.maplibre.android)

    // AppCompat — needed only for AppCompatDelegate.getApplicationLocales() in
    // MapViewModel.resolveAppLocale(), so map labels follow the in-app language picker
    // (SettingsScreen) rather than the device system locale.
    implementation(libs.androidx.appcompat)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Location — for the my-location FAB (one-shot "find me").
    implementation(libs.play.services.location)

    // Timber
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    // Robolectric — runs asset-bundling checks on the JVM without an emulator.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
