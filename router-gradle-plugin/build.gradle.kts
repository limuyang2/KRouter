plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
}

group = "io.github.limuyang2"
version = providers.gradleProperty("ROUTER_VERSION").orElse(
    providers.fileContents(layout.projectDirectory.file("../gradle.properties")).asText.map { content ->
        content.lineSequence().first { it.startsWith("ROUTER_VERSION=") }.substringAfter('=').trim()
    },
).get()

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// TestKit must load cooperating plugins in the same plugin classloader as the plugin under test.
val functionalPlugins = configurations.create("functionalPlugins")

dependencies {
    implementation(libs.android.gradle.api)
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    compileOnly(libs.ksp.gradle.plugin)
    implementation(libs.gson)
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
    functionalPlugins(libs.ksp.gradle.plugin)
    functionalPlugins(libs.android.gradle.plugin)
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(functionalPlugins)
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir("../router-index-protocol/src/main/kotlin")
}

gradlePlugin {
    plugins {
        create("router") {
            id = "io.github.limuyang2.router"
            implementationClass = "io.github.limuyang2.router.gradle.RouterPlugin"
            displayName = "KRouter Host Plugin"
            description = "Aggregates KRouter module indices into a static host registry."
        }
        create("routerModule") {
            id = "io.github.limuyang2.router.module"
            implementationClass = "io.github.limuyang2.router.gradle.RouterModulePlugin"
            displayName = "KRouter Module Plugin"
            description = "Produces stable KRouter module indices for dependency-aware registration."
        }
    }
}

apply(from = file("../gradle/maven-central.gradle.kts"))
