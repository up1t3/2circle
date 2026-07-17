plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM module — usable by :core:domain (also pure) and Android modules alike.
// No Android SDK, no Room, no Timber. Only coroutines (pure).
//
// jvmTarget MUST match the Android modules (17). Without this Kotlin defaults to the
// JDK toolchain (21 on this machine) and inline functions from this module fail to
// inline into Android bytecode with "Cannot inline bytecode built with JVM target 21".
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
