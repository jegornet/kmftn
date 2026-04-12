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
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("kmftn-base")
        description.set("Basic kmftn library")
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
