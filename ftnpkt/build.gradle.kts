@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
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
            dependencies {
                implementation(libs.kotlinx.io.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

tasks.register<JavaExec>("jvmRun") {
    classpath = kotlin.jvm().compilations["main"].runtimeDependencyFiles +
        kotlin.jvm().compilations["main"].output.allOutputs
    mainClass.set("net.jegor.kmftn.pktexample.MainKt")
}

mavenPublishing {
    pom {
        name.set("kmftn-ftnpkt")
        description.set("Library for handling packets and packed messages")
        url.set("https://github.com/jegornet/kmftn")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("jegornet")
                name.set("Jegor")
            }
        }
        scm {
            url.set("https://github.com/jegornet/kmftn")
            connection.set("scm:git:git://github.com/jegornet/kmftn.git")
            developerConnection.set("scm:git:ssh://github.com/jegornet/kmftn.git")
        }
    }
}
