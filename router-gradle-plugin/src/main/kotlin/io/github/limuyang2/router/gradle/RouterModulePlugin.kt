package io.github.limuyang2.router.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.google.devtools.ksp.gradle.KspAATask
import com.google.devtools.ksp.gradle.KspExtension
import io.github.limuyang2.router.index.RouterIndex
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

/** Produces a versioned router index next to each supported compilation's code artifact. */
class RouterModulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("router", RouterExtension::class.java)
        // Read lazily: plugins are applied before the build script configures project.group.
        extension.moduleId.convention(project.providers.provider {
            val group = project.group.toString()
            if (group.isEmpty()) project.name else "$group:${project.name}"
        })
        project.pluginManager.withPlugin("com.google.devtools.ksp") {
            project.extensions.getByType(KspExtension::class.java).arg("router.moduleId", extension.moduleId)
            project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                project.extensions.getByType(KotlinMultiplatformExtension::class.java).targets.all {
                    val target = this
                    compilations.all {
                        if (name == "main" && platformType != KotlinPlatformType.common) {
                            check(platformType in setOf(KotlinPlatformType.jvm, KotlinPlatformType.androidJvm, KotlinPlatformType.native, KotlinPlatformType.js, KotlinPlatformType.wasm)) {
                                "Router indices do not yet support target '${target.name}' ($platformType). Supported adapters: JVM, Android, Kotlin Native, JS, Wasm."
                            }
                            project.registerKotlinCompilation(extension, this, target.apiElementsConfigurationName, target.runtimeElementsConfigurationName)
                        }
                    }
                }
            }
            project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                val target = project.extensions.getByType(KotlinJvmProjectExtension::class.java).target
                target.compilations.all {
                    if (name == "main") project.registerKotlinCompilation(extension, this, target.apiElementsConfigurationName, target.runtimeElementsConfigurationName)
                }
            }
            project.pluginManager.withPlugin("com.android.application") {
                project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java).onVariants {
                    project.registerAndroidVariant(extension, it)
                }
            }
            project.pluginManager.withPlugin("com.android.library") {
                project.extensions.getByType(LibraryAndroidComponentsExtension::class.java).onVariants {
                    project.registerAndroidVariant(extension, it)
                }
            }
        }
        project.afterEvaluate {
            check(project.pluginManager.hasPlugin("com.google.devtools.ksp")) {
                "The router module plugin requires com.google.devtools.ksp and a router-ksp dependency for each compilation."
            }
            val moduleId = extension.moduleId.get()
            try {
                RouterIndex(moduleId, emptyList())
            } catch (error: IllegalArgumentException) {
                throw GradleException(
                    "Invalid router.moduleId '$moduleId' in ${project.path}. " +
                        "The default is project.group:project.name (project.name when group is empty). " +
                        "Set router { moduleId.set(\"com.example:feature-home\") } to a stable, unique identifier " +
                        "starting with an ASCII letter or digit and containing only ASCII letters, digits, '_', '.', ':', '-'.",
                    error,
                )
            }
        }
    }
}

internal data class RouterCompilation(
    val name: String,
    val compileTaskName: String,
    val compile: Configuration,
    val runtime: Configuration?,
    val index: TaskProvider<PrepareRouterIndexTask>,
    val sourceSet: KotlinSourceSet?,
)

@Suppress("DEPRECATION")
private fun Project.registerKotlinCompilation(
    extension: RouterExtension,
    compilation: KotlinCompilation<*>,
    apiElements: String,
    runtimeElements: String,
) {
    val compileName = compilation.compileTaskProvider.name
    // KSP derives its task name from the actual Kotlin compilation task (including custom target names).
    val kspName = compileName.replaceFirst("compile", "ksp")
    val unitName = if (compilation.target.name.isEmpty()) compilation.name
        else compilation.target.name + compilation.name.capitalized()
    registerRouterCompilation(
        extension, unitName, compileName, kspName,
        configurations.getByName(compilation.compileDependencyConfigurationName),
        compilation.runtimeDependencyConfigurationName?.let { configurations.findByName(it) },
        // KGP 2.4 publishes attribute/artifact copies in *-published configurations. Secondary
        // variants must also be attached to these copies so platform and root GMM advertise them.
        setOf(apiElements, runtimeElements, "$apiElements-published", "$runtimeElements-published"),
        compilation.defaultSourceSet,
    )
}

private fun Project.registerAndroidVariant(extension: RouterExtension, variant: Variant) {
    val name = variant.name
    // AGP's built-in Kotlin currently exposes these tasks by convention, isolated to this adapter.
    val unit = registerRouterCompilation(
        extension, name, "compile${name.capitalized()}Kotlin", "ksp${name.capitalized()}Kotlin",
        variant.compileConfiguration, variant.runtimeConfiguration,
        setOf("${name}ApiElements", "${name}RuntimeElements"),
    )
    // AGP uses separate publication configurations and deliberately strips single-variant
    // build-type/flavor attributes. Decorate each actual component's copies, preserving that model.
    components.all {
        val prefix = "${name}Variant${this.name.capitalized()}"
        configurations.matching { it.name == "${prefix}ApiPublication" || it.name == "${prefix}RuntimePublication" }.all {
            RouterArtifacts.publish(project, this, unit.index, name)
        }
    }
}

private fun Project.registerRouterCompilation(
    extension: RouterExtension,
    unitName: String,
    compileName: String,
    kspName: String,
    compile: Configuration,
    runtime: Configuration?,
    outgoing: Set<String>,
    sourceSet: KotlinSourceSet? = null,
): RouterCompilation {
    val index = tasks.register("prepare${unitName.capitalized()}RouterIndex", PrepareRouterIndexTask::class.java) {
        moduleId.set(extension.moduleId)
        val ksp = tasks.named(kspName, KspAATask::class.java)
        dependsOn(ksp)
        val resourcePath = extension.moduleId.map { RouterIndex(it, emptyList()).resourcePath }
        processorIndex.from(ksp.flatMap { it.kspConfig.resourceOutputDir }.zip(resourcePath) { directory, path -> directory.file(path) })
        hasSources.set(ksp.map { task ->
            (task.kspConfig.sourceRoots + task.kspConfig.commonSourceRoots + task.kspConfig.javaSourceRoots)
                .asFileTree.matching { include("**/*.kt", "**/*.java") }.files.isNotEmpty()
        })
        outputFile.set(layout.buildDirectory.file("routerIndex/$unitName/router-index.json"))
    }
    configurations.matching { it.name in outgoing }.all {
        RouterArtifacts.publish(project, this, index, unitName)
    }
    val unit = RouterCompilation(unitName, compileName, compile, runtime, index, sourceSet)
    extension.units.add(unit)
    extension.aggregate?.invoke(unit)
    return unit
}

internal fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }
