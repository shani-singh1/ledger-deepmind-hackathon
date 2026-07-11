plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.khataagent.agent"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            // litertlm-android 0.11.0 ships Kotlin 2.3.0 metadata; let our 2.1.21 compiler read it
            // (API-only consumption) instead of bumping the whole toolchain over slow venue wifi.
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }
}

dependencies {
    api(project(":core"))
    // NB: no :validate project dep — the orchestrator uses the core Validator interface,
    // injected at integration time. Keeps modules independently compilable in parallel.
    implementation(libs.litertlm.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
