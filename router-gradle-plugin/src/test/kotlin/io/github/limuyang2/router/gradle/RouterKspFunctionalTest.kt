package io.github.limuyang2.router.gradle

import io.github.limuyang2.router.index.RouterIndex
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Uses the real processor and Kotlin/KSP compilers; fixture outputs never touch repository build outputs. */
class RouterKspFunctionalTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `incremental changes do not pull unrelated sources into the router dirty set`() {
        val root = incrementalFixture()
        source(root, "Utility.kt", "package sample\nfun utility() = 1")
        source(root, "Unrelated.kt", "package sample\nclass Unrelated")
        run(root, ":host:compileKotlinDesktop")
        val before = routerOutputs(root)
        source(root, "Utility.kt", "package sample\nfun utility() = 2")
        val changed = run(root, ":host:compileKotlinDesktop")
        assertEquals(TaskOutcome.SUCCESS, changed.task(":feature:kspKotlinBackend")?.outcome)
        assertEquals(before, routerOutputs(root))
        val log = File(root, "feature/build").walkTopDown().single { it.name == "kspDirtySet.log" }.readText()
        assertTrue("Missing KSP dirty-set section:\n$log", log.contains("\nDirty:\n"))
        val dirty = log.substringAfter("\nDirty:\n").substringBefore("\nDirty / All:")
        assertTrue(dirty.contains("Utility.kt"))
        assertTrue("Aggregating routes must still be processed", dirty.contains("EarlyPage.kt"))
        assertFalse("Unchanged unrelated source was reprocessed:\n$log", dirty.contains("Unrelated.kt"))
        assertEquals(TaskOutcome.UP_TO_DATE, changed.task(":host:generateDesktopMainRouterAggregate")?.outcome)
        assertMatchesCleanBuild(root)
    }

    @Test fun `incremental route additions renames removals and empty modules match clean builds`() {
        val root = incrementalFixture()
        val candidate = source(root, "Candidate.kt", "package sample\nclass Candidate")
        run(root, ":host:compileKotlinDesktop")
        val originalIndex = featureIndex(root)

        // Adding an annotation to an existing, previously unrelated source must be discovered.
        candidate.writeText("package sample\n@io.github.limuyang2.router.anno.Router(host=\"example.com\", path=\"/candidate\") class Candidate")
        run(root, ":host:compileKotlinDesktop")
        assertTrue(featureProvider(root).contains("sample.Candidate::class"))
        assertTrue(featureProvider(root).contains("sample.EarlyPage::class"))
        assertEquals(originalIndex, featureIndex(root))
        assertMatchesCleanBuild(root)

        val renamed = source(root, "Renamed.kt", candidate.readText().replace("Candidate", "Renamed").replace("/candidate", "/renamed"))
        assertTrue(candidate.delete())
        run(root, ":host:compileKotlinDesktop")
        assertTrue(featureProvider(root).contains("sample.Renamed::class"))
        assertFalse(routerOutputs(root).toString().contains("Candidate"))
        assertMatchesCleanBuild(root)

        source(root, "EarlyPage.kt", "package sample\nclass EarlyPage")
        run(root, ":host:compileKotlinDesktop")
        assertFalse(featureProvider(root).contains("sample.EarlyPage::class"))
        assertTrue(featureProvider(root).contains("sample.Renamed::class"))
        assertTrue(renamed.delete())
        run(root, ":host:compileKotlinDesktop")
        assertTrue(featureIndex(root).registries.isEmpty())
        assertFalse(routerOutputs(root).keys.any { it.contains("ModuleRouterProvider_") || it.contains("ModuleRouterRegistry_") })
        assertMatchesCleanBuild(root)

        // The empty aggregating index has no contributing roots, but must notice a new route.
        source(root, "FirstAgain.kt", "package sample\n@io.github.limuyang2.router.anno.Router(host=\"example.com\", path=\"/again\") class FirstAgain")
        run(root, ":host:compileKotlinDesktop")
        assertEquals(originalIndex, featureIndex(root))
        assertTrue(featureProvider(root).contains("sample.FirstAgain::class"))
        assertMatchesCleanBuild(root)
    }

    @Test fun `incremental interceptor and entry changes preserve complete registration`() {
        val root = incrementalFixture()
        interceptorApi(root)
        source(root, "EntryPage.kt", """
            package sample
            @io.github.limuyang2.router.anno.Router(host="example.com", path="/entry")
            class EntryPage : io.github.limuyang2.router.IRouterEntryProvider
        """)
        source(root, "PushBase.kt", "package sample\nopen class PushBase : io.github.limuyang2.router.IRouterPushInterceptor")
        val interceptors = listOf("GlobalPush", "GlobalPop", "OwnerPush", "OwnerPop").associateWith { name ->
            val owner = name.startsWith("Owner")
            val direction = if (name.endsWith("Push")) "Push" else "Pop"
            source(root, "$name.kt", """
                package sample
                @io.github.limuyang2.router.anno.Router${name}Interceptor(${if (owner) "owners=[EarlyPage::class], priorities=[1]" else "priority=1"})
                class $name : ${if (name == "OwnerPush") "PushBase()" else "io.github.limuyang2.router.IRouter${direction}Interceptor"}
            """, sourceSet = "backendMain")
        }
        run(root, ":host:compileKotlinDesktop")
        assertTrue(featureProvider(root).contains("entry<sample.EntryPage>"))
        interceptors.keys.forEach { assertTrue(featureProvider(root).contains("sample.$it()")) }

        // Change each kind independently so a missing source association cannot hide behind another.
        interceptors.forEach { (name, file) ->
            file.writeText(file.readText().replace("=[1]", "=[7]").replace("priority=1", "priority=7"))
            run(root, ":host:compileKotlinDesktop")
            val provider = featureProvider(root)
            val registration = if (name.startsWith("Owner")) "priority = 7, interceptor = sample.$name()"
                else "interceptor = sample.$name(), priority = 7"
            assertTrue("Updated $name registration missing:\n$provider", provider.contains(registration))
            interceptors.keys.forEach { assertTrue(provider.contains("sample.$it()")) }
        }
        assertMatchesCleanBuild(root)

        // Rebind both owner interceptors; no stale association may remain on EarlyPage.
        interceptors.filterKeys { it.startsWith("Owner") }.values.forEach { file ->
            file.writeText(file.readText().replace("EarlyPage::class", "EntryPage::class"))
        }
        run(root, ":host:compileKotlinDesktop")
        val provider = featureProvider(root)
        val early = provider.substringAfter("sample.EarlyPage::class to ParseRouterInfo(").substringBefore("sample.EntryPage::class to ParseRouterInfo(")
        assertFalse(early.contains("sample.OwnerPush"))
        assertFalse(early.contains("sample.OwnerPop"))
        val entry = provider.substringAfter("sample.EntryPage::class to ParseRouterInfo(").substringBefore("override fun routeAliasesKV")
        assertTrue(entry.contains("sample.OwnerPush()"))
        assertTrue(entry.contains("sample.OwnerPop()"))
        assertMatchesCleanBuild(root)

        // A non-annotated supertype remains a real dependency through KSP resolution tracing.
        source(root, "PushBase.kt", "package sample\nopen class PushBase")
        val invalid = GradleRunner.create().withProjectDir(root).withPluginClasspath()
            .withArguments(":feature:kspKotlinBackend", "--console=plain", "--max-workers=2", "-Dorg.gradle.jvmargs=-Xmx1g").buildAndFail()
        assertTrue(invalid.output.contains("must implement IRouterPushInterceptor"))
        source(root, "PushBase.kt", "package sample\nopen class PushBase : io.github.limuyang2.router.IRouterPushInterceptor")
        run(root, ":host:compileKotlinDesktop")
        assertMatchesCleanBuild(root)

        interceptors.values.forEach { assertTrue(it.delete()) }
        source(root, "EntryPage.kt", "package sample\n@io.github.limuyang2.router.anno.Router(host=\"example.com\", path=\"/entry\") class EntryPage")
        run(root, ":host:compileKotlinDesktop")
        val withoutInterceptors = featureProvider(root)
        interceptors.keys.forEach { assertFalse(withoutInterceptors.contains("sample.$it")) }
        assertFalse(withoutInterceptors.contains("entry<sample.EntryPage>"))
        assertTrue(withoutInterceptors.contains("sample.EntryPage::class"))
        assertMatchesCleanBuild(root)
    }

    @Test fun `incremental multi-round generation retains deferred routes`() {
        val root = incrementalFixture(lateRoutes = true)
        run(root, ":host:compileKotlinDesktop")
        val original = routerOutputs(root)
        source(root, "Utility.kt", "package sample\nfun utility() = 1", sourceSet = "backendMain")
        run(root, ":host:compileKotlinDesktop")
        assertEquals(original, routerOutputs(root))
        assertTrue(featureProvider(root).contains("sample.EarlyPage::class"))
        assertTrue(featureProvider(root).contains("sample.LatePage::class"))
        assertMatchesCleanBuild(root)
    }

    @Test fun `custom targets compile complete multi-round registries and reuse configuration cache`() {
        val root = fixture(lateRoutes = true)
        run(root, ":host:compileKotlinDesktop", "--configuration-cache")
        val index = RouterIndex.decode(File(root, "feature/build/routerIndex/backendMain/router-index.json").readText())
        assertEquals("example:feature", index.moduleId)
        val generated = File(root, "feature/build/generated/ksp/backend/backendMain/kotlin").walkTopDown()
            .single { it.name.startsWith("ModuleRouterProvider_") }.readText()
        assertTrue(generated.contains("sample.EarlyPage::class"))
        assertTrue(generated.contains("sample.LatePage::class"))
        val aggregate = File(root, "host/build/generated/routerAggregate/desktopMain/kotlin/sample/AllRoutes.kt").readText()
        assertTrue(aggregate.contains(index.registries.single()))
        val second = run(root, ":host:compileKotlinDesktop", "--configuration-cache")
        assertTrue(second.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":host:generateDesktopMainRouterAggregate")?.outcome)
    }

    @Test fun `deleting final route produces empty index without stale registration`() {
        val root = fixture(lateRoutes = false)
        run(root, ":host:compileKotlinDesktop")
        File(root, "feature/src/commonMain/kotlin/sample/EarlyPage.kt").writeText("package sample\nclass Unannotated\n")
        run(root, ":host:compileKotlinDesktop")
        val index = RouterIndex.decode(File(root, "feature/build/routerIndex/backendMain/router-index.json").readText())
        assertTrue(index.registries.isEmpty())
        val generated = File(root, "feature/build/generated/ksp/backend/backendMain/kotlin")
        assertFalse(generated.walkTopDown().any { it.name.startsWith("ModuleRouterRegistry_") })
        assertFalse(File(root, "host/build/generated/routerAggregate/desktopMain/kotlin/sample/AllRoutes.kt").readText().contains("ModuleRouterRegistry"))
        File(root, "feature/src/commonMain/kotlin/sample/EarlyPage.kt").delete()
        run(root, ":host:compileKotlinDesktop")
        assertTrue(RouterIndex.decode(File(root, "feature/build/routerIndex/backendMain/router-index.json").readText()).registries.isEmpty())
    }

    @Test fun `aggregate is exposed as generated source without becoming a KSP input`() {
        val root = fixture(lateRoutes = false)
        File(root, "host/build.gradle").appendText("""

            router { registryName.set(providers.gradleProperty('entryName').getOrElse('AllRoutes')) }
            tasks.register('verifyRouterSourceModel') {
                doLast {
                    def directory = layout.buildDirectory.dir('generated/routerAggregate/desktopMain/kotlin').get().asFile
                    assert kotlin.sourceSets.desktopMain.generatedKotlin.srcDirs.contains(directory)
                    assert !kotlin.sourceSets.desktopMain.kotlin.srcDirs.contains(directory)
                    assert !kotlin.sourceSets.commonMain.generatedKotlin.srcDirs.contains(directory)
                    def kspTask = tasks.getByName('kspKotlinDesktop')
                    assert !(kspTask.kspConfig.sourceRoots.files + kspTask.kspConfig.javaSourceRoots.files + kspTask.kspConfig.commonSourceRoots.files)
                        .any { it.toPath().startsWith(directory.toPath()) }
                    assert !kspTask.taskDependencies.getDependencies(kspTask).contains(tasks.getByName('generateDesktopMainRouterAggregate'))
                }
            }
        """.trimIndent())
        run(root, ":host:compileKotlinDesktop", ":host:verifyRouterSourceModel")
        val renamed = run(root, ":host:generateDesktopMainRouterAggregate", ":host:verifyRouterSourceModel", "-PentryName=Renamed")
        assertEquals(TaskOutcome.UP_TO_DATE, renamed.task(":host:kspKotlinDesktop")?.outcome)
        val directory = File(root, "host/build/generated/routerAggregate/desktopMain/kotlin/sample")
        assertFalse(File(directory, "AllRoutes.kt").exists())
        assertTrue(File(directory, "Renamed.kt").isFile)
    }

    @Test fun `explicit module identity overrides the default throughout KSP and aggregation`() {
        val root = fixture(lateRoutes = false)
        run(root, ":host:compileKotlinDesktop")
        val indexFile = File(root, "feature/build/routerIndex/backendMain/router-index.json")
        val original = RouterIndex.decode(indexFile.readText())
        val script = File(root, "feature/build.gradle")
        script.appendText("\nrouter { moduleId.set('custom:stable-feature') }\n")
        run(root, ":host:compileKotlinDesktop", "--configuration-cache")
        val overridden = RouterIndex.decode(indexFile.readText())
        assertEquals("custom:stable-feature", overridden.moduleId)
        assertNotEquals(original.registries, overridden.registries)
        val aggregate = File(root, "host/build/generated/routerAggregate/desktopMain/kotlin/sample/AllRoutes.kt").readText()
        assertTrue(aggregate.contains(overridden.registries.single()))
        assertFalse(aggregate.contains(original.registries.single()))
        val generated = File(root, "feature/build/generated/ksp/backend/backendMain/kotlin")
        assertEquals(1, generated.walkTopDown().count { it.name.startsWith("ModuleRouterRegistry_") })
        script.appendText("\ngroup='renamed'; version='2.0'\n")
        run(root, ":host:compileKotlinDesktop")
        assertEquals(overridden, RouterIndex.decode(indexFile.readText()))
    }

    @Test fun `KMP publication exposes matching index through root metadata redirect`() {
        val root = fixture(lateRoutes = false)
        run(root, ":api:publish", ":feature:publish")
        val result = run(root, ":host:compileKotlinDesktop", "-Pexternal=true")
        assertNull(result.task(":feature:prepareBackendMainRouterIndex"))
        assertTrue(File(root, "host/build/generated/routerAggregate/desktopMain/kotlin/sample/AllRoutes.kt").readText().contains("ModuleRouterRegistry"))
    }

    @Test fun `standalone Kotlin JVM host consumes local and published router modules`() {
        val root = fixture(lateRoutes = false)
        listOf("feature", "host").forEach { name ->
            File(root, "$name/src/${if (name == "feature") "commonMain" else "desktopMain"}").renameTo(File(root, "$name/src/main"))
            File(root, "$name/build.gradle").writeText("""
                plugins { id 'org.jetbrains.kotlin.jvm'; id 'java-library'; id 'com.google.devtools.ksp'; id '${if (name == "host") "io.github.limuyang2.router" else "io.github.limuyang2.router.module"}'; id 'maven-publish' }
                group='example'; version='1.0'
                kotlin { jvmToolchain(21) }
                dependencies {
                    if (providers.gradleProperty('external').isPresent()) api('example:${if (name == "host") "feature" else "api"}:1.0')
                    else api(project(':${if (name == "host") "feature" else "api"}'))
                    ksp(project(':processor'))
                }
                router { registryPackage.set('sample'); registryName.set('AllRoutes') }
                publishing { publications { lib(MavenPublication) { from components.java } }; repositories { maven { url=rootProject.layout.projectDirectory.dir('repo') } } }
            """.trimIndent())
        }
        run(root, ":host:compileKotlin")
        run(root, ":api:publish", ":feature:publish")
        run(root, ":host:compileKotlin", "-Pexternal=true")
        assertTrue(File(root, "host/build/generated/routerAggregate/main/kotlin/sample/AllRoutes.kt").readText().contains("ModuleRouterRegistry"))
    }

    @Test fun `Android flavor host consumes matching library index without a KSP cycle`() {
        val sdk = System.getenv("ANDROID_HOME")?.let(::File) ?: File(System.getProperty("user.home"), "Library/Android/sdk")
        assumeTrue("Android SDK 36 is required for this integration test", File(sdk, "platforms/android-36").isDirectory)
        val root = fixture(lateRoutes = false)
        File(root, "local.properties").writeText("sdk.dir=${sdk.invariantSeparatorsPath}\n")
        listOf("feature", "host").forEach { name ->
            File(root, "$name/src/${if (name == "host") "desktopMain" else "commonMain"}").renameTo(File(root, "$name/src/main"))
            val plugin = if (name == "host") "com.android.application" else "com.android.library"
            val routerPlugin = if (name == "host") "io.github.limuyang2.router" else "io.github.limuyang2.router.module"
            File(root, "$name/build.gradle").writeText("""
                plugins { id '$plugin'; id 'com.google.devtools.ksp'; id '$routerPlugin'; id 'maven-publish' }
                group='example'; version='1.0'
                android {
                    namespace='sample.$name'; compileSdk=36
                    defaultConfig { minSdk=26 }
                    flavorDimensions += 'edition'
                    productFlavors { demo { dimension='edition' }; full { dimension='edition' } }
                    ${if (name == "host") "buildTypes { staging { initWith debug; matchingFallbacks=['debug'] } }" else "publishing { singleVariant('demoDebug') }"}
                    compileOptions { sourceCompatibility=JavaVersion.VERSION_21; targetCompatibility=JavaVersion.VERSION_21 }
                }
                dependencies {
                    if (providers.gradleProperty('external').isPresent()) implementation('example:${if (name == "host") "feature" else "api"}:1.0')
                    else implementation(project(':${if (name == "host") "feature" else "api"}'))
                    ksp(project(':processor'))
                }
                router { registryPackage.set('sample'); registryName.set('AllRoutes') }
                ${if (name == "feature") "afterEvaluate { publishing { publications { lib(MavenPublication) { from components.demoDebug } }; repositories { maven { url=rootProject.layout.projectDirectory.dir('repo') } } } }" else ""}
            """.trimIndent())
            File(root, "$name/src/main/AndroidManifest.xml").apply {
                parentFile.mkdirs(); writeText("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application /></manifest>")
            }
        }
        val result = run(root, ":host:assembleDemoDebug")
        assertFalse(result.output.contains("More than one file was found with OS independent path 'META-INF/io.github.limuyang2.router/"))
        assertNotNull(result.task(":feature:prepareDemoDebugRouterIndex"))
        assertNull(result.task(":feature:prepareFullDebugRouterIndex"))
        assertTrue(File(root, "host/build/generated/routerAggregate/demoDebug/kotlin/sample/AllRoutes.kt").readText().contains("ModuleRouterRegistry"))
        run(root, ":host:compileDemoStagingKotlin")
        run(root, ":api:publish", ":feature:publish")
        val published = run(root, ":host:compileDemoStagingKotlin", "-Pexternal=true")
        assertNull(published.task(":feature:prepareDemoDebugRouterIndex"))
        assertTrue(File(root, "host/build/generated/routerAggregate/demoStaging/kotlin/sample/AllRoutes.kt").readText().contains("ModuleRouterRegistry"))
    }

    @Test fun `iOS compilation consumes local and published platform indices`() {
        assumeTrue("Kotlin Native iOS integration requires macOS", System.getProperty("os.name").contains("Mac"))
        val root = fixture(lateRoutes = false)
        File(root, "api/src/main").renameTo(File(root, "api/src/commonMain"))
        File(root, "api/build.gradle").writeText("""
            plugins { id 'org.jetbrains.kotlin.multiplatform'; id 'maven-publish' }
            group='example'; version='1.0'
            kotlin { jvm(); iosArm64(); iosSimulatorArm64() }
            publishing { repositories { maven { url=rootProject.layout.projectDirectory.dir('repo') } } }
        """.trimIndent())
        listOf("feature", "host").forEach { name ->
            val script = File(root, "$name/build.gradle")
            script.writeText(script.readText()
                .replace("jvm('${if (name == "feature") "backend" else "desktop"}')", "jvm('${if (name == "feature") "backend" else "desktop"}'); iosArm64(); iosSimulatorArm64()")
                .replace(if (name == "feature") "backendMain { dependencies" else "desktopMain { dependencies", "commonMain { dependencies") + "\ndependencies { add('kspIosArm64', project(':processor')); add('kspIosSimulatorArm64', project(':processor')) }\n")
        }
        listOf("iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSet ->
            File(root, "host/src/$sourceSet/kotlin/sample/Start.kt").apply {
                parentFile.mkdirs(); writeText("package sample\nfun start() = AllRoutes.registerAll()")
            }
        }
        run(root, ":host:compileKotlinIosArm64", ":host:compileKotlinIosSimulatorArm64")
        run(root, ":api:publish", ":feature:publish")
        run(root, ":host:compileKotlinIosSimulatorArm64", "-Pexternal=true")
        val nativeIndex = RouterIndex.decode(File(root, "feature/build/routerIndex/iosSimulatorArm64Main/router-index.json").readText())
        val jvmIndex = RouterIndex.decode(File(root, "feature/build/routerIndex/backendMain/router-index.json").readText())
        assertEquals(jvmIndex, nativeIndex)
    }

    @Test fun `Android KMP modules publish and consume indices through root coordinates`() {
        val sdk = System.getenv("ANDROID_HOME")?.let(::File) ?: File(System.getProperty("user.home"), "Library/Android/sdk")
        assumeTrue("Android SDK 36 is required for this integration test", File(sdk, "platforms/android-36").isDirectory)
        val root = fixture(lateRoutes = false)
        File(root, "local.properties").writeText("sdk.dir=${sdk.invariantSeparatorsPath}\n")
        File(root, "api/src/main").renameTo(File(root, "api/src/commonMain"))
        listOf("api", "feature", "host").forEach { name ->
            val routerPlugin = when (name) {
                "host" -> "id 'io.github.limuyang2.router'"
                "feature" -> "id 'io.github.limuyang2.router.module'"
                else -> ""
            }
            File(root, "$name/build.gradle").writeText("""
                plugins { id 'org.jetbrains.kotlin.multiplatform'; id 'com.android.kotlin.multiplatform.library'; id 'maven-publish'; id 'com.google.devtools.ksp'; $routerPlugin }
                group='example'; version='1.0'
                kotlin {
                    jvmToolchain(21)
                    android { namespace='sample.$name'; compileSdk=36; minSdk=26 }
                    jvm('${if (name == "host") "desktop" else "backend"}')
                    ${if (name == "api") "" else "sourceSets { commonMain { dependencies { if (providers.gradleProperty('external').isPresent()) api('example:${if (name == "host") "feature" else "api"}:1.0'); else api(project(':${if (name == "host") "feature" else "api"}')) } } }"}
                }
                ${if (name == "api") "" else "dependencies { add('kspAndroid', project(':processor')); add('ksp${if (name == "host") "Desktop" else "Backend"}', project(':processor')) }; router { registryPackage.set('sample'); registryName.set('AllRoutes') }"}
                publishing { repositories { maven { url=rootProject.layout.projectDirectory.dir('repo') } } }
            """.trimIndent())
        }
        File(root, "host/src/androidMain/kotlin/sample/Start.kt").apply {
            parentFile.mkdirs(); writeText("package sample\nfun start() = AllRoutes.registerAll()")
        }
        run(root, ":host:compileAndroidMain")
        run(root, ":api:publish", ":feature:publish")
        val result = run(root, ":host:compileAndroidMain", "-Pexternal=true")
        assertNull(result.task(":feature:prepareAndroidMainRouterIndex"))
        assertTrue(File(root, "host/build/generated/routerAggregate/androidMain/kotlin/sample/AllRoutes.kt").readText().contains("ModuleRouterRegistry"))
    }

    private fun incrementalFixture(lateRoutes: Boolean = false): File = fixture(lateRoutes).also {
        File(it, "gradle.properties").writeText("ksp.incremental=true\nksp.incremental.log=true\n")
    }

    private fun source(root: File, name: String, text: String, sourceSet: String = "commonMain"): File =
        File(root, "feature/src/$sourceSet/kotlin/sample/$name").apply {
            parentFile.mkdirs()
            writeText(text.trimIndent())
        }

    private fun routerOutputs(root: File): Map<String, String> = listOf(
        File(root, "feature/build/generated/ksp"),
        File(root, "feature/build/routerIndex"),
        File(root, "host/build/generated/routerAggregate"),
    ).flatMap { directory -> directory.walkTopDown().filter { it.isFile }.toList() }
        .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }

    private fun featureIndex(root: File): RouterIndex =
        RouterIndex.decode(File(root, "feature/build/routerIndex/backendMain/router-index.json").readText())

    private fun featureProvider(root: File): String = File(root, "feature/build/generated/ksp")
        .walkTopDown().single { it.isFile && it.name.startsWith("ModuleRouterProvider_") }.readText()

    private fun interceptorApi(root: File) {
        val api = File(root, "api/src/main/kotlin/io/github/limuyang2/router/Api.kt")
        api.writeText(api.readText()
            .replace("typealias EntryProviderInstaller = () -> Unit", """
                interface IRouterPushInterceptor
                interface IRouterPopInterceptor
                interface IRouterEntryProvider {
                    fun contentKey(): Any = "entry"
                    fun metadata(): Map<String, Any> = emptyMap()
                    fun UI() {}
                }
                class EntryBuilder {
                    inline fun <reified T> entry(noinline clazzContentKey: (T) -> Any, noinline metadata: (T) -> Map<String, Any>, noinline content: (T) -> Unit) {}
                }
                typealias EntryProviderInstaller = EntryBuilder.() -> Unit
            """.trimIndent())
            .replace("class GlobalPushInterceptorInfo", "class GlobalPushInterceptorInfo(val interceptor: IRouterPushInterceptor, val priority: Int)")
            .replace("class GlobalPopInterceptorInfo", "class GlobalPopInterceptorInfo(val interceptor: IRouterPopInterceptor, val priority: Int)"))
        File(root, "api/src/main/kotlin/io/github/limuyang2/router/info/Interceptors.kt").writeText("""
            package io.github.limuyang2.router.info
            class ParsePagePushInterceptorInfo(val priority: Int, val interceptor: io.github.limuyang2.router.IRouterPushInterceptor)
            class ParsePagePopInterceptorInfo(val priority: Int, val interceptor: io.github.limuyang2.router.IRouterPopInterceptor)
        """.trimIndent())
        File(root, "api/src/main/kotlin/io/github/limuyang2/router/anno/Interceptors.kt").writeText("""
            package io.github.limuyang2.router.anno
            import kotlin.reflect.KClass
            annotation class RouterGlobalPushInterceptor(val priority: Int = 10)
            annotation class RouterGlobalPopInterceptor(val priority: Int = 10)
            annotation class RouterOwnerPushInterceptor(val owners: Array<KClass<*>>, val priorities: IntArray = [])
            annotation class RouterOwnerPopInterceptor(val owners: Array<KClass<*>>, val priorities: IntArray = [])
        """.trimIndent())
    }

    private fun assertMatchesCleanBuild(root: File) {
        val incremental = routerOutputs(root)
        run(root, ":feature:clean", ":host:clean", ":host:compileKotlinDesktop")
        assertEquals("Incremental output differs from clean compilation", incremental, routerOutputs(root))
    }

    private fun fixture(lateRoutes: Boolean): File {
        val root = temporary.newFolder()
        val repository = File(System.getProperty("user.dir")).parentFile
        fun write(path: String, value: String) {
            File(root, path).apply { parentFile.mkdirs(); writeText(value.trimIndent()) }
        }
        write("settings.gradle", """
            pluginManagement { repositories { maven { url='https://maven.aliyun.com/repository/gradle-plugin' }; mavenCentral(); google(); gradlePluginPortal() } }
            rootProject.name='ksp-fixture'; include 'processor','api','feature','host'
        """)
        write("build.gradle", """
            plugins {
                id 'io.github.limuyang2.router' apply false
                id 'com.google.devtools.ksp' apply false
            }
            allprojects {
                repositories { maven { url=rootProject.layout.projectDirectory.dir('repo') }; maven { url='https://maven.aliyun.com/repository/public' }; mavenCentral(); google() }
            }
        """)
        write("processor/build.gradle", """
            plugins { id 'org.jetbrains.kotlin.jvm' }
            kotlin { jvmToolchain(21) }
            kotlin.sourceSets.main.kotlin.srcDirs('${repository.resolve("lib_router_ksp/src/main/java").invariantSeparatorsPath}', '${repository.resolve("router-index-protocol/src/main/kotlin").invariantSeparatorsPath}')
            dependencies {
                implementation('com.google.devtools.ksp:symbol-processing-api:2.3.10')
                implementation('com.google.code.gson:gson:2.11.0')
                implementation('org.apache.httpcomponents:httpclient:4.5.14')
            }
        """)
        write("processor/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider",
            "io.github.limuyang2.router_ksp.RouteProcessorProvider\n" + if (lateRoutes) "fixture.LateProvider\n" else "")
        if (lateRoutes) write("processor/src/main/kotlin/fixture/LateProvider.kt", """
            package fixture
            import com.google.devtools.ksp.processing.*
            import com.google.devtools.ksp.symbol.KSAnnotated
            class LateProvider : SymbolProcessorProvider {
                override fun create(environment: SymbolProcessorEnvironment) = object : SymbolProcessor {
                    var generated = false
                    override fun process(resolver: Resolver): List<KSAnnotated> {
                        if (!generated && environment.options["router.moduleId"] == "example:feature") {
                            generated = true
                            environment.codeGenerator.createNewFile(Dependencies(true), "sample", "LatePage").bufferedWriter().use {
                                it.write("package sample\nopen class LateBase\n@io.github.limuyang2.router.anno.Router(host = \"example.com\", path = \"/late\") class LatePage")
                            }
                        }
                        return emptyList()
                    }
                }
            }
        """)
        write("api/build.gradle", """
            plugins { id 'org.jetbrains.kotlin.jvm'; id 'java-library'; id 'maven-publish' }
            group='example'; version='1.0'
            kotlin { jvmToolchain(21) }
            publishing { publications { lib(MavenPublication) { from components.java } }; repositories { maven { url=rootProject.layout.projectDirectory.dir('repo') } } }
        """)
        write("api/src/main/kotlin/io/github/limuyang2/router/anno/Router.kt", """
            package io.github.limuyang2.router.anno
            annotation class Router(val scheme: String="", val host: String="", val path: String="", val desc: String="")
        """)
        write("api/src/main/kotlin/io/github/limuyang2/router/Api.kt", """
            package io.github.limuyang2.router
            import kotlin.reflect.KClass
            import io.github.limuyang2.router.info.ParseRouterInfo
            annotation class RouterKeep
            interface IRouterInstance { fun instance(extras: Map<String, Any>?): Any }
            typealias EntryProviderInstaller = () -> Unit
            class GlobalPushInterceptorInfo
            class GlobalPopInterceptorInfo
            interface ModuleRouter {
                fun routeMasterKV(): Map<KClass<*>, ParseRouterInfo>
                fun routeAliasesKV(): Map<String, KClass<*>>
                fun globalPushInterceptors(): List<GlobalPushInterceptorInfo>
                fun globalPopInterceptors(): List<GlobalPopInterceptorInfo>
                fun entryProviderInstaller(): EntryProviderInstaller
            }
            object RouterSingleton { class Config(val defaultScheme: String="https"); fun getConfig() = Config() }
            fun registerModuleRouters(router: ModuleRouter) {}
        """)
        write("api/src/main/kotlin/io/github/limuyang2/router/info/ParseRouterInfo.kt", """
            package io.github.limuyang2.router.info
            import io.github.limuyang2.router.IRouterInstance
            class ParseRouterInfo(val url: String, val scheme: String, val host: String, val path: String, val desc: String,
                val pushInterceptors: List<Any>, val popInterceptors: List<Any>, val instanceProvider: IRouterInstance)
        """)
        write("feature/build.gradle", """
            plugins { id 'org.jetbrains.kotlin.multiplatform'; id 'com.google.devtools.ksp'; id 'io.github.limuyang2.router.module'; id 'maven-publish' }
            group='example'; version='1.0'
            kotlin {
                jvmToolchain(21); jvm('backend')
                sourceSets { backendMain { dependencies { api(project(':api')) } } }
            }
            dependencies { add('kspBackend', project(':processor')) }
            publishing { repositories { maven { url=rootProject.layout.projectDirectory.dir('repo') } } }
        """)
        write("feature/src/${if (lateRoutes) "backendMain" else "commonMain"}/kotlin/sample/EarlyPage.kt", """
            package sample
            @io.github.limuyang2.router.anno.Router(host="example.com", path="/early") class EarlyPage${if (lateRoutes) " : LateBase()" else ""}
        """)
        write("host/build.gradle", """
            plugins { id 'org.jetbrains.kotlin.multiplatform'; id 'com.google.devtools.ksp'; id 'io.github.limuyang2.router' }
            group='example'
            kotlin {
                jvmToolchain(21); jvm('desktop')
                sourceSets { desktopMain { dependencies {
                    if (providers.gradleProperty('external').isPresent()) implementation('example:feature:1.0')
                    else implementation(project(':feature'))
                } } }
            }
            dependencies { add('kspDesktop', project(':processor')) }
            router { registryPackage.set('sample'); registryName.set('AllRoutes') }
        """)
        write("host/src/desktopMain/kotlin/sample/Host.kt", "package sample\nfun start() = AllRoutes.registerAll()")
        return root
    }

    private fun run(root: File, vararg args: String) = GradleRunner.create().withProjectDir(root).withPluginClasspath().forwardOutput()
        .withArguments(*args, "--stacktrace", "--console=plain", "--max-workers=2", "-Dorg.gradle.jvmargs=-Xmx1g").build()
}
