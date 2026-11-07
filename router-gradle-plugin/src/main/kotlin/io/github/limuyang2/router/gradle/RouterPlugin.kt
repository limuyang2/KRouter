package io.github.limuyang2.router.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

/** Host plugin: includes the producer role and aggregates the actual compilation's router indices. */
class RouterPlugin : Plugin<Project> {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    override fun apply(project: Project) {
        project.pluginManager.apply(RouterModulePlugin::class.java)
        val extension = project.extensions.getByType(RouterExtension::class.java)
        val ownComponent = project.path
        val aggregate: (RouterCompilation) -> Unit = { unit ->
            val aggregateDir = project.layout.buildDirectory.dir("generated/routerAggregate/${unit.name}/kotlin")
            val task = project.tasks.register("generate${unit.name.capitalized()}RouterAggregate", GenerateRouterAggregateTask::class.java) {
                registryPackage.set(extension.registryPackage)
                registryName.set(extension.registryName)
                outputDir.set(aggregateDir)
                indices.add(unit.index.flatMap { it.outputFile }.map { RouterIndexInput(ownComponent, it.asFile) })
                val dependencies = RouterArtifacts.indices(project, unit.compile)
                indices.addAll(dependencies.inputs)
                dependsOn(dependencies.files)
                unit.runtime?.let { inaccessibleRuntimeComponents.set(RouterArtifacts.inaccessible(unit.compile, it)) }
                dependsOn(unit.index)
            }
            val sourceSet = unit.sourceSet
            if (sourceSet != null) {
                // KGP exposes generatedKotlin to the IDE and compiler; KSP reads sourceSet.kotlin.
                // Keeping the aggregate out of the latter preserves KSP -> aggregate -> compile.
                sourceSet.generatedKotlin.srcDir(task.flatMap { it.outputDir })
            } else {
                // Ordinary AGP variants use a separate source model. The compiler wiring stays
                // authoritative: dependsOn + source() keep ordering and inputs. The aggregate is
                // additionally registered as a *static* variant Kotlin source so Android Studio
                // resolves it. AGP feeds variant sources to KSP, but the aggregate is unannotated
                // (zero KSP output) and static registration carries no task dependency, so unlike
                // addGeneratedSourceDirectory it can never order KSP after the aggregate task.
                project.tasks.matching { it.name == unit.compileTaskName }.configureEach {
                    dependsOn(task)
                    (this as KotlinCompileTool).source(task.flatMap { it.outputDir })
                }
                if (extension.registerIdeSources.get()) {
                    val directory = aggregateDir.get().asFile
                    val path = if (directory.startsWith(project.projectDir))
                        project.projectDir.toPath().relativize(directory.toPath()).toString()
                    else directory.absolutePath
                    unit.androidVariant?.sources?.kotlin?.addStaticSourceDirectory(path)
                }
            }
        }
        extension.aggregate = aggregate
        extension.units.forEach(aggregate)
    }
}
