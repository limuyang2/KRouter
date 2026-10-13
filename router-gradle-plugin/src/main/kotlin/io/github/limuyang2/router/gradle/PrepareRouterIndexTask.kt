package io.github.limuyang2.router.gradle

import io.github.limuyang2.router.index.RouterIndex
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Validates the processor output and provides an empty index for a compilation with no source files. */
@CacheableTask
abstract class PrepareRouterIndexTask : DefaultTask() {
    @get:Input abstract val moduleId: Property<String>
    @get:Input abstract val hasSources: Property<Boolean>
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val processorIndex: ConfigurableFileCollection
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun prepare() {
        val id = moduleId.get()
        val files = processorIndex.files.filter { it.isFile }
        require(files.size <= 1) { "Multiple processor indices for $id" }
        val index = if (!hasSources.get()) {
            RouterIndex(id, emptyList())
        } else {
            check(files.isNotEmpty()) {
                "Missing router index for $id. Add router-ksp to this compilation's KSP configuration and use matching processor/plugin versions."
            }
            RouterIndex.decode(files.single().readText()).also {
                require(it.moduleId == id) { "Processor moduleId '${it.moduleId}' differs from router.moduleId '$id'" }
            }
        }
        outputFile.get().asFile.writeIfChanged(index.encode())
    }
}
