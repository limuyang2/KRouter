pluginManagement {
    includeBuild("router-gradle-plugin")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()

//        maven("https://maven.aliyun.com/repository/gradle-plugin")
//        maven("https://maven.aliyun.com/repository/public")
//        maven("https://maven.aliyun.com/repository/central")
//        maven("https://maven.aliyun.com/repository/google")
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    // Kotlin Web tooling adds temporary, module-filtered Node.js/Yarn distribution repositories.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()

//        maven("https://maven.aliyun.com/repository/public")
//        maven("https://maven.aliyun.com/repository/central")
//        maven("https://maven.aliyun.com/repository/google")

        maven {
            url = uri("https://nexus.qimingdaren.com/repository/android/")
            credentials {
                username = "android"
                password = "qimingdaren@android-dev-Ov60c27T"
            }
        }
    }
}

rootProject.name = "library-router"
include(":androidApp")
include(":composeApp")
include(":lib_router")
include(":lib_router_ksp")
include(":desktopApp")
include(":demo-feature-product")
include(":demo-feature-account")
include(":demo-feature-shared")
include(":webApp")
