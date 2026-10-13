plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.limuyang2"
version = property("ROUTER_VERSION") as String

java {
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
        }
        create("routerModule") {
            id = "io.github.limuyang2.router.module"
            implementationClass = "io.github.limuyang2.router.gradle.RouterModulePlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri(providers.gradleProperty("MAVEN_URL").get())
            credentials {
                username = providers.gradleProperty("MAVEN_USERNAME").get()
                password = providers.gradleProperty("MAVEN_PASSWORD").get()
            }
        }
    }
}
