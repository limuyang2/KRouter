package io.github.limuyang2.router.gradle

import io.github.limuyang2.router.index.RouterIndex
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Exercises real variant selection/publication using tiny Java components, independent of Android SDKs. */
class RouterArtifactsFunctionalTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `local graph includes transitive router once and ignores unrelated projects`() {
        val root = fixture()
        val result = run(root, ":host:aggregate")
        val source = output(root).readText()
        assertEquals(1, Regex("sample.FeatureRegistry.registerAll").findAll(source).count())
        assertTrue(source.contains("sample.HostRegistry.registerAll()"))
        assertFalse(source.contains("Unrelated"))
        assertNull(result.task(":unrelated:prepareIndex"))
        assertNotNull(result.task(":feature:prepareIndex"))
    }

    @Test fun `indices work from published Maven metadata and selected version wins`() {
        val root = fixture()
        run(root, ":feature:publish")
        File(root, "feature/index.json").writeText(RouterIndex("example:feature", listOf("sample.NewRegistry")).encode())
        run(root, ":feature:publish", "-Prelease=2.0")
        run(root, ":host:aggregate", "-Pexternal=true")
        val source = output(root).readText()
        assertTrue(source.contains("sample.NewRegistry.registerAll()"))
        assertFalse(source.contains("sample.FeatureRegistry"))
        val metadata = File(root, "repo/example/feature/2.0/feature-2.0.module").readText()
        assertTrue(metadata.contains("apiElementsRouterIndex"))
    }

    @Test fun `unchanged and unrelated source changes do not rerun aggregate and rename removes old output`() {
        val root = fixture()
        run(root, ":host:aggregate", "--configuration-cache")
        val second = run(root, ":host:aggregate", "--configuration-cache")
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":host:aggregate")?.outcome)
        assertTrue(second.output.contains("Reusing configuration cache"))
        File(root, "feature/unrelated.kt").writeText("// unrelated processor output")
        assertEquals(TaskOutcome.UP_TO_DATE, run(root, ":host:aggregate").task(":host:aggregate")?.outcome)
        run(root, ":host:aggregate", "-PaggregateName=Renamed")
        assertFalse(output(root).exists())
        assertTrue(File(root, "host/build/aggregate/sample/Renamed.kt").isFile)
    }

    @Test fun `duplicate identities and duplicate registry names fail with component owners`() {
        val root = fixture()
        File(root, "host/index.json").writeText(RouterIndex("example:feature", emptyList()).encode())
        val duplicate = fail(root, ":host:aggregate", "-PmoduleIdentity=example:feature").output
        assertTrue(duplicate.contains("Duplicate router.moduleId 'example:feature'"))
        assertTrue(duplicate.contains("moduleId.set(\"com.example:feature-home\")"))
        assertTrue(duplicate.contains("rebuild or republish"))
        File(root, "host/index.json").writeText(RouterIndex("example:host", listOf("sample.FeatureRegistry")).encode())
        assertTrue(fail(root, ":host:aggregate").output.contains("Duplicate router registry 'sample.FeatureRegistry'"))
    }

    @Test fun `runtime only router is diagnosed instead of silently omitted`() {
        val root = fixture()
        val result = fail(root, ":host:aggregate", "-Phidden=true")
        assertTrue(result.output.contains("absent from the compile graph"))
        assertTrue(result.output.contains("feature"))
    }

    @Test fun `aggregate name cannot shadow a dependency registry`() {
        val root = fixture()
        assertTrue(fail(root, ":host:aggregate", "-PaggregateName=FeatureRegistry").output.contains("collides with a module registry"))
    }

    @Test fun `marked component without index and invalid index fail`() {
        val root = fixture()
        val result = fail(root, ":host:aggregate", "-Pmissing=true")
        assertTrue(result.output.contains("router-index") || result.output.contains("Missing or ambiguous router index"))
        File(root, "feature/index.json").writeText("{}")
        assertTrue(fail(root, ":host:aggregate").output.contains("Unsupported schemaVersion"))
    }

    @Test fun `empty producer index removes last registry from host`() {
        val root = fixture()
        run(root, ":host:aggregate")
        File(root, "feature/index.json").writeText(RouterIndex("example:feature", emptyList()).encode())
        run(root, ":host:aggregate")
        assertFalse(output(root).readText().contains("sample.FeatureRegistry"))
    }

    @Test fun `build cache restores aggregate in a different checkout directory`() {
        val first = fixture()
        val second = fixture()
        val cache = temporary.newFolder("cache").invariantSeparatorsPath
        listOf(first, second).forEach {
            File(it, "settings.gradle").appendText("buildCache { local { directory=file('$cache') } }\n")
        }
        run(first, ":host:aggregate", "--build-cache")
        val restored = run(second, ":host:aggregate", "--build-cache")
        assertEquals(TaskOutcome.FROM_CACHE, restored.task(":host:aggregate")?.outcome)
        assertEquals(output(first).readText(), output(second).readText())
    }

    @Test fun `included build substitution uses the selected local producer`() {
        val root = fixture()
        val included = File(root, "included").apply { mkdirs() }
        File(included, "settings.gradle").writeText("rootProject.name='feature'\n")
        File(included, "build.gradle").writeText(File(root, "feature/build.gradle").readText()
            .replace("plugins { id 'java-library';", "plugins { id 'io.github.limuyang2.router' apply false; id 'java-library';"))
        File(root, "feature/index.json").copyTo(File(included, "index.json"))
        File(root, "settings.gradle").appendText("includeBuild('included')\n")
        val result = run(root, ":host:aggregate", "-Pexternal=true")
        assertNull(result.task(":feature:prepareIndex"))
        assertTrue(output(root).readText().contains("sample.FeatureRegistry.registerAll()"))
    }

    private fun fixture(): File {
        val root = temporary.newFolder()
        File(root, "settings.gradle").writeText("rootProject.name='router-fixture'; include 'host','feature','bridge','plain','unrelated'\n")
        File(root, "build.gradle").writeText("""
            plugins { id 'io.github.limuyang2.router' apply false }
            allprojects { repositories { maven { url = rootProject.layout.projectDirectory.dir('repo') } } }
        """.trimIndent())
        fun module(name: String, script: String) {
            File(root, name).mkdirs()
            File(root, "$name/build.gradle").writeText(script.trimIndent())
        }
        val imports = """
            import io.github.limuyang2.router.gradle.RouterArtifacts
            import io.github.limuyang2.router.gradle.PrepareRouterIndexTask
            import io.github.limuyang2.router.gradle.GenerateRouterAggregateTask
            import io.github.limuyang2.router.gradle.RouterIndexInput
        """.trimIndent()
        val prepare = """
            def index = tasks.register('prepareIndex', PrepareRouterIndexTask) {
                moduleId.set(providers.gradleProperty('moduleIdentity').getOrElse("example:" + project.name))
                hasSources.set(true)
                processorIndex.from(layout.projectDirectory.file('index.json'))
                outputFile.set(layout.buildDirectory.file('router/index.json'))
            }
        """.trimIndent()
        module("feature", """
            $imports
            plugins { id 'java-library'; id 'maven-publish' }
            group='example'; version=providers.gradleProperty('release').getOrElse('1.0')
            $prepare
            ['apiElements','runtimeElements'].each { name ->
                def cfg = configurations.getByName(name)
                if (providers.gradleProperty('missing').isPresent()) cfg.attributes.attribute(RouterArtifacts.INSTANCE.marker, 1)
                else RouterArtifacts.INSTANCE.publish(project, cfg, index, 'main')
            }
            publishing {
                publications { lib(MavenPublication) { from components.java } }
                repositories { maven { url=rootProject.layout.projectDirectory.dir('repo') } }
            }
        """)
        module("bridge", """
            plugins { id 'java-library' }
            dependencies {
                if (providers.gradleProperty('external').isPresent()) api('example:feature:1.0')
                else if (!providers.gradleProperty('hidden').isPresent()) api(project(':feature'))
            }
        """)
        module("plain", "plugins { id 'java-library' }")
        module("unrelated", """
            $imports
            plugins { id 'java-library' }
            $prepare
            ['apiElements','runtimeElements'].each { RouterArtifacts.INSTANCE.publish(project, configurations.getByName(it), index, 'main') }
        """)
        module("host", """
            $imports
            plugins { id 'java-library' }
            $prepare
            dependencies {
                implementation(project(':plain')); implementation(project(':bridge'))
                if (providers.gradleProperty('external').isPresent()) implementation('example:feature:2.0')
                else if (providers.gradleProperty('hidden').isPresent()) runtimeOnly(project(':feature'))
                else implementation(project(':feature'))
            }
            def selected = RouterArtifacts.INSTANCE.indices(project, configurations.compileClasspath)
            def inaccessible = RouterArtifacts.INSTANCE.inaccessible(configurations.compileClasspath, configurations.runtimeClasspath)
            tasks.register('aggregate', GenerateRouterAggregateTask) {
                registryPackage.set('sample')
                registryName.set(providers.gradleProperty('aggregateName').getOrElse('Aggregate'))
                outputDir.set(layout.buildDirectory.dir('aggregate'))
                indices.add(new RouterIndexInput('host', layout.buildDirectory.file('router/index.json').get().asFile))
                indices.addAll(selected.inputs)
                dependsOn(selected.files)
                inaccessibleRuntimeComponents.set(inaccessible)
                dependsOn(index)
            }
        """)
        listOf("feature", "host", "unrelated").forEach { name ->
            File(root, "$name/index.json").writeText(RouterIndex("example:$name", listOf("sample.${name.replaceFirstChar { it.uppercaseChar() }}Registry")).encode())
        }
        return root
    }

    private fun output(root: File) = File(root, "host/build/aggregate/sample/Aggregate.kt")
    private fun runner(root: File, args: Array<out String>) = GradleRunner.create()
        .withProjectDir(root).withPluginClasspath()
        .withArguments(*args, "--stacktrace", "--console=plain", "--max-workers=2", "-Dorg.gradle.jvmargs=-Xmx768m")
    private fun run(root: File, vararg args: String) = runner(root, args).build()
    private fun fail(root: File, vararg args: String) = runner(root, args).buildAndFail()
}
