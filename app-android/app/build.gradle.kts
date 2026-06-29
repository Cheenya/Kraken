import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun gitOutput(workingDir: File, vararg args: String): String =
    try {
        val output = ByteArrayOutputStream()
        val process = ProcessBuilder(listOf("git", *args))
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.use { it.copyTo(output) }
        if (process.waitFor() == 0) {
            output.toString().trim().ifBlank { "unknown" }
        } else {
            "unknown"
        }
    } catch (_: Exception) {
        "unknown"
    }

fun gitStatusPorcelain(workingDir: File): String =
    try {
        val output = ByteArrayOutputStream()
        val process = ProcessBuilder(listOf("git", "status", "--porcelain"))
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.use { it.copyTo(output) }
        if (process.waitFor() == 0) {
            output.toString().trim()
        } else {
            "unknown-status"
        }
    } catch (_: Exception) {
        "unknown-status"
    }

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.disser.kraken"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.disser.kraken"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val gitRoot = rootDir.parentFile
        val gitSha = gitOutput(gitRoot, "rev-parse", "--short", "HEAD")
        val sourceState = if (gitStatusPorcelain(gitRoot).isBlank()) {
            "clean_commit_$gitSha"
        } else {
            "dirty_working_tree_based_on_$gitSha"
        }
        buildConfigField("String", "KRAKEN_VERSION_NAME", buildConfigString(versionName.orEmpty()))
        buildConfigField("String", "GIT_SHA", buildConfigString(gitSha))
        buildConfigField("String", "SOURCE_STATE", buildConfigString(sourceState))

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "KRAKEN_RESEARCH_DEMO", "true")
            buildConfigField("boolean", "KRAKEN_RELEASE_SECURITY_READY", "false")
        }
        getByName("release") {
            buildConfigField("boolean", "KRAKEN_RESEARCH_DEMO", "false")
            buildConfigField("boolean", "KRAKEN_RELEASE_SECURITY_READY", "false")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

val krakenReleasePrototypeGate = tasks.register("krakenReleasePrototypeGate") {
    group = "verification"
    description = "Blocks release-like builds until production crypto, identity and QR gates are implemented."
    doLast {
        throw GradleException(
            "Kraken release/prod build is blocked: PrototypeNoSecurityPacketCrypto, " +
                "placeholder identity and unsigned QR handshake are still research-only. " +
                "Use assembleDebug for friend testing, or implement the production readiness roadmap first.",
        )
    }
}

tasks.matching { task ->
    task.name in setOf("assembleRelease", "bundleRelease", "packageRelease")
}.configureEach {
    dependsOn(krakenReleasePrototypeGate)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    val cameraxVersion = "1.4.1"
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
