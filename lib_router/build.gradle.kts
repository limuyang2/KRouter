plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "io.github.limuyang2.router"
        compileSdk = 36
        minSdk = 26

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "routerKit"
            isStatic = true
            freeCompilerArgs += listOf(
                "-Xbinary=bundleId=io.github.limuyang2.router"
            )
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.navigation3.runtime)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
        }

        iosMain.dependencies {
        }

        jvmMain.dependencies {
        }
    }
}

val mGroupId = "io.github.limuyang2"
val mArtifactId = "router"
val mVersion = property("ROUTER_VERSION") as String

group = mGroupId
version = mVersion

publishing {
    publications.withType<MavenPublication>().configureEach {
        groupId = mGroupId
        version = mVersion

        artifactId = when (name) {
            "kotlinMultiplatform" -> mArtifactId
            "android" -> "$mArtifactId-android"
            "jvm" -> "$mArtifactId-jvm"
            "iosArm64" -> "$mArtifactId-ios-arm64"
            "iosSimulatorArm64" -> "$mArtifactId-ios-simulator-arm64"
            else -> artifactId
        }
    }

    repositories {
        maven {
            url = uri(property("MAVEN_URL") as String)
            credentials {
                username = property("MAVEN_USERNAME") as String
                password = property("MAVEN_PASSWORD") as String
            }
        }
    }
}
