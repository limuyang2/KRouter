package io.github.limuyang2.router.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/** Shared configuration for router producers and hosts. */
abstract class RouterExtension @Inject constructor(objects: ObjectFactory) {
    /**
     * Stable identity shared by all targets/variants of this logical module.
     * Defaults to `project.group:project.name`, or just `project.name` when group is empty.
     * Set explicitly (e.g. `com.example:feature-home`) to resolve collisions or retain the identity
     * when renaming the project/group. Versions, targets and filesystem paths are not included.
     */
    val moduleId: Property<String> = objects.property(String::class.java)

    /** Package of the host's generated aggregate entry point. Does not rename dependency registries. */
    val registryPackage: Property<String> = objects.property(String::class.java)
        .convention("io.github.limuyang2.router.generated")

    /** Simple class name of the host entry point whose `registerAll()` registers all selected modules. */
    val registryName: Property<String> = objects.property(String::class.java)
        .convention("RouterAggregatedRegistry")

    internal val units = mutableListOf<RouterCompilation>()
    internal var aggregate: ((RouterCompilation) -> Unit)? = null
}
