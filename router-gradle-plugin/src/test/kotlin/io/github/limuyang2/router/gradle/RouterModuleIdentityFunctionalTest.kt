package io.github.limuyang2.router.gradle

import io.github.limuyang2.router.index.RouterIndex
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RouterModuleIdentityFunctionalTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `default identity supports empty group late group configuration and relocated build cache`() {
        val root = fixture()
        run(root, "prepareMainRouterIndex", "--configuration-cache")
        assertEquals("identity-fixture", index(root).moduleId)
        val reused = run(root, "prepareMainRouterIndex", "--configuration-cache")
        assertTrue(reused.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.UP_TO_DATE, reused.task(":prepareMainRouterIndex")?.outcome)

        val relocated = fixture()
        val cache = temporary.newFolder("cache").invariantSeparatorsPath
        listOf(root, relocated).forEach {
            File(it, "settings.gradle").appendText("buildCache { local { directory=file('$cache') } }\n")
        }
        run(root, "prepareMainRouterIndex", "-PtestGroup=com.example", "-PtestVersion=1.0", "--build-cache")
        assertEquals("com.example:identity-fixture", index(root).moduleId)
        val restored = run(relocated, "prepareMainRouterIndex", "-PtestGroup=com.example", "-PtestVersion=2.0", "--build-cache")
        assertEquals(TaskOutcome.FROM_CACHE, restored.task(":prepareMainRouterIndex")?.outcome)
        assertEquals(index(root), index(relocated))
    }

    @Test fun `invalid inferred and explicit identities suggest an override and valid override takes priority`() {
        val root = fixture()
        val invalid = fail(root, "prepareMainRouterIndex", "-PtestGroup=invalid/group").output
        assertTrue(invalid.contains("Invalid router.moduleId 'invalid/group:identity-fixture'"))
        assertTrue(invalid.contains("moduleId.set(\"com.example:feature-home\")"))
        run(root, "prepareMainRouterIndex", "-PtestGroup=invalid/group", "-PtestModuleId=custom:stable")
        assertEquals("custom:stable", index(root).moduleId)
        val explicit = fail(root, "prepareMainRouterIndex", "-PtestModuleId=").output
        assertTrue(explicit.contains("Invalid router.moduleId ''"))
    }

    @Test fun `inferred collision names both modules and can be repaired by overriding the producer`() {
        val root = fixture(name = "feature")
        File(root, "settings.gradle").appendText("include 'feature'\n")
        File(root, "build.gradle").appendText("\ngroup='example'\nrepositories { maven { url=layout.projectDirectory.dir('repo') } }\n")
        val producer = File(root, "feature/build.gradle").apply {
            parentFile.mkdirs()
            writeText("""
                plugins { id 'org.jetbrains.kotlin.jvm'; id 'com.google.devtools.ksp'; id 'io.github.limuyang2.router.module'; id 'maven-publish' }
                group='example'; version='1.0'
                repositories { mavenCentral() }
                publishing {
                    publications { lib(MavenPublication) { from components.java; artifactId='feature-library' } }
                    repositories { maven { url=rootProject.layout.projectDirectory.dir('repo') } }
                }
            """.trimIndent())
        }
        // Different published coordinates can carry the same inferred router identity. Using
        // identical Gradle coordinates locally would trigger Gradle's own conflict resolution.
        run(root, ":feature:publish")
        File(root, "build.gradle").appendText("\ndependencies { implementation('example:feature-library:' + providers.gradleProperty('dependencyVersion').getOrElse('1.0')) }\n")
        val duplicate = fail(root, ":generateMainRouterAggregate").output
        assertTrue(duplicate, duplicate.contains("Duplicate router.moduleId 'example:feature'"))
        assertTrue(duplicate, duplicate.contains("in : and"))
        assertTrue(duplicate, duplicate.contains("example:feature-library:1.0"))
        assertTrue(duplicate, duplicate.contains("moduleId.set(\"com.example:feature-home\")"))
        producer.appendText("\nrouter { moduleId.set('example:feature-library') }\nversion='2.0'\n")
        run(root, ":feature:publish")
        run(root, ":generateMainRouterAggregate", "-PdependencyVersion=2.0")
        assertEquals("example:feature", index(root).moduleId)
        assertEquals("example:feature-library", index(File(root, "feature")).moduleId)
    }

    private fun fixture(name: String = "identity-fixture"): File = temporary.newFolder().apply {
        File(this, "settings.gradle").writeText("rootProject.name='$name'\n")
        File(this, "build.gradle").writeText("""
            plugins { id 'org.jetbrains.kotlin.jvm'; id 'com.google.devtools.ksp'; id 'io.github.limuyang2.router' }
            repositories { mavenCentral() }
            if (providers.gradleProperty('testGroup').isPresent()) group=providers.gradleProperty('testGroup').get()
            version=providers.gradleProperty('testVersion').getOrElse('1.0')
            if (providers.gradleProperty('testModuleId').isPresent()) router { moduleId.set(providers.gradleProperty('testModuleId')) }
        """.trimIndent())
    }

    private fun index(root: File) = RouterIndex.decode(File(root, "build/routerIndex/main/router-index.json").readText())
    private fun runner(root: File, args: Array<out String>) = GradleRunner.create()
        .withProjectDir(root).withPluginClasspath()
        .withArguments(*args, "--stacktrace", "--console=plain", "--max-workers=2", "-Dorg.gradle.jvmargs=-Xmx768m")
    private fun run(root: File, vararg args: String) = runner(root, args).build()
    private fun fail(root: File, vararg args: String) = runner(root, args).buildAndFail()
}
