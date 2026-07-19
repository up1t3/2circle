plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.twocircle.bike.feature.routing.brouter"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    implementation(project(":core:data"))

    // BRouter offline routing engine — fat-jar, only this module sees btools.* classes.
    // Other modules consume [BRouterFacade] which exposes only our own domain types,
    // keeping the BRouter dependency isolated (and out of KSP analysis scope elsewhere).
    // `files(...)` rather than `fileTree` — fileTree's include pattern has been flaky
    // with KSP symbol resolution; explicit files() is the canonical form.
    api(files("libs/brouter-1.7.10-all.jar"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
}
