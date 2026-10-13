// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    base
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

group = "io.github.limuyang2"
version = providers.gradleProperty("ROUTER_VERSION").get()
description = "Kotlin Multiplatform routing for Compose Navigation 3."

tasks.register("prepareMavenCentralPublication") {
    group = "publishing"
    description = "本地生成全部组件的 POM、源码及文档 JAR，不签名、不上传"
    dependsOn(
        ":lib_router:prepareMavenCentralPublication",
        ":lib_router_ksp:prepareMavenCentralPublication",
        gradle.includedBuild("router-gradle-plugin").task(":prepareMavenCentralPublication"),
    )
}

tasks.register("stageMavenCentralPublication") {
    group = "publishing"
    description = "构建、签名全部组件并发布到本地 RepoDir，不上传"
    dependsOn(
        ":lib_router:publishAllPublicationsToCentralStagingRepository",
        ":lib_router_ksp:publishAllPublicationsToCentralStagingRepository",
        gradle.includedBuild("router-gradle-plugin")
            .task(":publishAllPublicationsToCentralStagingRepository"),
    )
}

val publicationVersion = version.toString()
val bundleMavenCentral = tasks.register<Zip>("bundleMavenCentral") {
    group = "publishing"
    description = "将本地签名产物打包为 Central Portal 手动上传 ZIP，不上传"
    dependsOn("stageMavenCentralPublication")
    archiveFileName.set("KRouter-$publicationVersion.zip")
    destinationDirectory.set(layout.projectDirectory.dir("RepoDir"))
    duplicatesStrategy = DuplicatesStrategy.FAIL
    includeEmptyDirs = false
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(layout.projectDirectory.dir("RepoDir"))
    include("io/**/$publicationVersion/**")
    exclude("**/maven-metadata.xml*")
}

tasks.register("publishAll") {
    group = "publishing"
    description = "仅在本地构建、签名并打包全部组件，生成手动上传 ZIP"
    dependsOn(bundleMavenCentral)
}
