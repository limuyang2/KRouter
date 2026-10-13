plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.lint)
    id("io.github.limuyang2.router")
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "io.github.limuyang2.router.demo.compose"
        compileSdk = 37
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
            baseName = "ComposeApp"
            isStatic = true
            freeCompilerArgs += listOf(
                "-Xbinary=bundleId=io.github.limuyang2.router.demo.compose"
            )
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":lib_router"))
                implementation(project(":demo-feature-product"))
                implementation(project(":demo-feature-account"))
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.compose.components.resources)
                implementation(libs.androidx.navigation3.ui)
                implementation(libs.androidx.lifecycle.viewmodel.navigation3)
                implementation(libs.kotlin.collections.immutable)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }

        iosMain.dependencies {
        }

        jvmMain.dependencies {
        }

        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// KSP per-target：每个 target 独立编译，各自生成唯一命名的模块路由 provider + registry。
dependencies {
    add("kspAndroid", project(":lib_router_ksp"))
    add("kspJvm", project(":lib_router_ksp"))
    add("kspIosArm64", project(":lib_router_ksp"))
    add("kspIosSimulatorArm64", project(":lib_router_ksp"))
}

// 聚合当前编译依赖的模块索引，生成 RouterAggregatedRegistry。
router {
    registryPackage.set("io.github.limuyang2.router.demo.generated")
    registryName.set("RouterAggregatedRegistry")
}
