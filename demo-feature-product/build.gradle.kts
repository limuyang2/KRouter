import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("io.github.limuyang2.router.module")
}

kotlin {
    jvmToolchain(21)
    android {
        namespace = "io.github.limuyang2.router.demo.feature.product"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    jvm()

    js {
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":lib_router"))
            // Expose the shared router module so the host can aggregate its registry transitively.
            api(project(":demo-feature-shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.navigation3.ui)
        }
    }
}

dependencies {
    add("kspAndroid", project(":lib_router_ksp"))
    add("kspJvm", project(":lib_router_ksp"))
    add("kspJs", project(":lib_router_ksp"))
    add("kspWasmJs", project(":lib_router_ksp"))
    add("kspIosArm64", project(":lib_router_ksp"))
    add("kspIosSimulatorArm64", project(":lib_router_ksp"))
}

// moduleId is inferred from the project group and name.
