@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val versionName = findProperty("VERSION_NAME") as String

val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.resolve("net/jegor/kmftn/binkpclient")
        dir.mkdirs()
        dir.resolve("BuildConfig.kt").writeText(
            """
            |package net.jegor.kmftn.binkpclient
            |
            |internal object BuildConfig {
            |    const val VERSION_NAME: String = "$versionName"
            |}
            """.trimMargin()
        )
    }
}

kotlin {
    explicitApi()

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    macosX64()
    macosArm64()

    linuxX64()
    linuxArm64()

    mingwX64()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generateBuildConfig)
            dependencies {
                api(project(":base"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.io.core)
                implementation(libs.ktor.io)
                implementation(libs.ktor.network)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlincrypto.hash.md)
                implementation(libs.kotlincrypto.hash.sha1)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// Integration tests require fork()/exec() which are unavailable on iOS
tasks.matching { it.name.matches(Regex(".*[Tt]est.*[Ii]os.*|.*[Ii]os.*[Tt]est.*")) }.configureEach {
    enabled = false
}
