import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension
import java.util.Properties

// All three components publish to the same root RepoDir, including the included build.
// Never clean this shared directory from an individual publication task.
val stagingDirectory = project.projectDir.parentFile.resolve("RepoDir")

// Like pag-cmp: use the existing secring.gpg with Gradle's built-in signing plugin.
// The included build reads the same root local.properties; secrets never enter tracked files.
val signingProperties = Properties()
val signingPropertiesFile = project.projectDir.parentFile.resolve("local.properties")
if (signingPropertiesFile.isFile) {
    signingPropertiesFile.reader(Charsets.UTF_8).use(signingProperties::load)
}
listOf("signing.keyId", "signing.secretKeyRingFile", "signing.password").forEach { key ->
    val value = providers.gradleProperty(key).orNull ?: signingProperties.getProperty(key)
    if (value != null) extra[key] = value
}

extensions.configure<PublishingExtension> {
    repositories.maven {
        name = "CentralStaging"
        url = stagingDirectory.toURI()
    }
    publications.withType<MavenPublication>().configureEach {
        // Gradle plugin markers are POM-only; never attach JARs to them.
        if (!name.endsWith("PluginMarkerMaven")) {
            val publicationName = name
            // Every publication needs its own file, otherwise Sign tasks share the same .asc output.
            val centralJavadocJar = tasks.register<Jar>("${publicationName}CentralJavadocJar") {
                archiveBaseName.set("${project.name}-$publicationName")
                archiveClassifier.set("javadoc")
                from(project.projectDir.parentFile.resolve("README.md"))
                from(project.projectDir.parentFile.resolve("LICENSE"))
            }
            artifact(centralJavadocJar)
        }
        pom {
            name.set(when (project.name) {
                "lib_router" -> "KRouter Runtime"
                "lib_router_ksp" -> "KRouter KSP Processor"
                else -> "KRouter Gradle Plugin"
            })
            description.set(when (project.name) {
                "lib_router" -> "Kotlin Multiplatform routing for Compose Navigation 3."
                "lib_router_ksp" -> "Generates KRouter route factories, registries and module indices."
                else -> "Builds and aggregates KRouter module indices across platforms."
            })
            inceptionYear.set("2026")
            url.set("https://github.com/limuyang2/KRouter")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("limuyang2")
                    name.set("Mumu")
                    url.set("https://github.com/limuyang2")
                }
            }
            scm {
                url.set("https://github.com/limuyang2/KRouter")
                connection.set("scm:git:https://github.com/limuyang2/KRouter.git")
                developerConnection.set("scm:git:ssh://git@github.com/limuyang2/KRouter.git")
            }
            issueManagement {
                system.set("GitHub")
                url.set("https://github.com/limuyang2/KRouter/issues")
            }
        }
    }
}

val centralPublications = extensions.getByType<PublishingExtension>().publications
extensions.configure<SigningExtension> {
    sign(centralPublications)
}

tasks.register("prepareMavenCentralPublication") {
    group = "publishing"
    description = "Generate POMs, sources and documentation locally without signing or uploading"
    dependsOn(tasks.withType<GenerateMavenPom>())
    dependsOn(tasks.withType<Jar>().matching { it.name.endsWith("CentralJavadocJar") })
    dependsOn(tasks.withType<Jar>().matching { it.name.endsWith("SourcesJar", ignoreCase = true) })
}
