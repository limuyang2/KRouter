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
    implementation("com.android.tools.build:gradle-api:9.2.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.10")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
    functionalPlugins("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.10")
    functionalPlugins("com.android.tools.build:gradle:9.2.1")
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
