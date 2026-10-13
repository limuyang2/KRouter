// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register("publishAll") {
    description = "发布 router runtime / router-ksp / router-gradle-plugin 三个组件到 Maven 仓库"
    dependsOn(
        ":lib_router:publish",
        ":lib_router_ksp:publish",
        gradle.includedBuild("router-gradle-plugin").task(":publish"),
    )
}
