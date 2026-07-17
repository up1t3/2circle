plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.twocircle.bike.data"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        minSdk = 26
        // Room schema export — checked into git for migration safety.
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Robolectric: needs merged manifest/resources + relaxed Android stubs so Room and
    // SupportSQLite can run on the JVM in unit tests (no emulator required).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))

    // Room — primary on-device store. KSP processor generates DAO impls.
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt for repository injection.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Serialization — for manifest.json parsing.
    implementation(libs.kotlinx.serialization.json)

    // Network — shared OkHttp client exposed via Hilt to feature modules.
    api(libs.okhttp)
    api(libs.okhttp.logging)

    // Coroutines.
    implementation(libs.kotlinx.coroutines.android)

    // Testing.
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.turbine)
}
