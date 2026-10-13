package io.github.limuyang2.router.gradle

import io.github.limuyang2.router.index.RouterIndex
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.Serializable

/** A component identity and its exact index artifact; paths are excluded from cache identity. */
data class RouterIndexInput(
    @get:Input val component: String,
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) val file: File,
) : Serializable

/** Generates static registrations using only indices from the selected compilation dependency graph. */
@CacheableTask
abstract class GenerateRouterAggregateTask : DefaultTask() {
    @get:Nested
    abstract val indices: ListProperty<RouterIndexInput>

    /** Runtime router modules whose generated registry is not visible to this compilation. */
    @get:Input
    abstract val inaccessibleRuntimeComponents: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val registryPackage: Property<String>

    @get:Input
    abstract val registryName: Property<String>

    init {
        indices.convention(emptyList())
        inaccessibleRuntimeComponents.convention(emptyList())
    }

    @TaskAction
    fun generate() {
        check(inaccessibleRuntimeComponents.get().isEmpty()) {
            "Router registries are present at runtime but absent from the compile graph: " +
                inaccessibleRuntimeComponents.get().joinToString() +
                ". Add explicit host dependencies, align compile/runtime versions, or expose them with api; runtimeOnly/hidden implementation routes cannot be statically registered."
        }
        RouterIndex.requireQualifiedName(registryPackage.get())
        RouterIndex.requireQualifiedName(registryName.get())
        require('.' !in registryName.get()) { "router.registryName must be a simple Kotlin identifier" }
        val modules = linkedMapOf<String, String>()
        val registries = sortedMapOf<String, String>()
        indices.get().sortedBy { it.component }.forEach { input ->
            val index = try {
                RouterIndex.decode(input.file.readText())
            } catch (error: Exception) {
                throw IllegalArgumentException("Invalid router index from ${input.component}: ${error.message}", error)
            }
            val previous = modules.putIfAbsent(index.moduleId, input.component)
            require(previous == null) {
                "Duplicate router.moduleId '${index.moduleId}' in $previous and ${input.component}. " +
                    "Set a unique ID with router { moduleId.set(\"com.example:feature-home\") } " +
                    "in one of these modules, then rebuild or republish it. " +
                    "The default project.group:project.name may collide across projects or builds."
            }
            index.registries.forEach { name ->
                val owner = registries.putIfAbsent(name, input.component)
                require(owner == null) { "Duplicate router registry '$name' in $owner and ${input.component}" }
            }
        }
        val aggregateName = "${registryPackage.get()}.${registryName.get()}"
        require(aggregateName !in registries) { "Aggregate entry '$aggregateName' collides with a module registry from ${registries[aggregateName]}" }
        val source = buildString {
            appendLine("package ${registryPackage.get()}")
            appendLine()
            appendLine("object ${registryName.get()} {")
            appendLine("    fun registerAll() {")
            registries.keys.forEach { appendLine("        $it.registerAll()") }
            appendLine("    }")
            appendLine("}")
        }
        val directory = outputDir.get().asFile
        val output = File(directory, registryPackage.get().replace('.', '/') + "/${registryName.get()}.kt")
        // This directory belongs exclusively to this task, including output from previous names/packages.
        directory.walkTopDown().filter { it.isFile && it != output }.forEach {
            check(it.delete()) { "Cannot remove stale router aggregate: $it" }
        }
        output.writeIfChanged(source)
        logger.lifecycle("[router] Aggregated ${registries.size} module registry(ies) into ${registryName.get()}")
    }
}

internal fun File.writeIfChanged(content: String) {
    if (!isFile || readText() != content) {
        parentFile.mkdirs()
        writeText(content)
    }
}
