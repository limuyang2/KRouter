package io.github.limuyang2.router.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/** Index selection is an artifact view of the code graph, never a separately resolved dependency graph. */
internal data class RouterIndexArtifacts(val files: FileCollection, val inputs: Provider<List<RouterIndexInput>>)
private data class RouterCodeComponent(val artifactOwner: ComponentIdentifier, val attributes: Map<String, String>)

internal object RouterArtifacts {
    val marker: Attribute<Int> = Attribute.of("io.github.limuyang2.router.index-schema", Int::class.javaObjectType)
    private const val INDEX_TYPE = "router-index"

    fun publish(project: Project, configuration: Configuration, task: TaskProvider<PrepareRouterIndexTask>, classifier: String) {
        configuration.attributes.attribute(marker, 1)
        configuration.outgoing.variants.create("routerIndex") {
            // Code consumers request category=library, so the index cannot become a code candidate.
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.DOCUMENTATION))
            attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.objects.named(DocsType::class.java, INDEX_TYPE))
            attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, INDEX_TYPE)
            artifact(task.flatMap { it.outputFile }) {
                type = INDEX_TYPE
                extension = "json"
                this.classifier = "router-index-$classifier"
            }
        }
    }

    fun indices(project: Project, configuration: Configuration): RouterIndexArtifacts {
        val selected = configuration.incoming.resolutionResult.rootComponent.map(::routerComponents)
        val category = project.objects.named(Category::class.java, Category.DOCUMENTATION)
        val docsType = project.objects.named(DocsType::class.java, INDEX_TYPE)
        fun view(local: Boolean) = configuration.incoming.artifactView {
            // Local secondary variants belong to the already-selected code configuration.
            // In Maven/GMM they become top-level variants, so only external artifacts are reselected.
            if (!local) withVariantReselection()
            copyAttributes(configuration.attributes, attributes)
            attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, INDEX_TYPE)
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, category)
            attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, docsType)
            componentFilter { id ->
                val components = selected.get()
                (id is ProjectComponentIdentifier) == local &&
                    (components.containsKey(id) || components.values.any { it.artifactOwner == id })
            }
        }
        val local = view(true)
        val external = view(false)
        val allArtifacts = local.artifacts.resolvedArtifacts.zip(external.artifacts.resolvedArtifacts) { a, b -> a + b }
        val inputs = allArtifacts.zip(selected) { artifacts, components ->
            val byComponent = artifacts.groupBy { it.id.componentIdentifier }
            components.values.groupBy { it.artifactOwner }.map { (component, selections) ->
                val codeAttributes = selections.map { it.attributes }.distinct().singleOrNull()
                    ?: throw GradleException("Multiple router code variants selected for $component")
                val artifact = byComponent[component]?.singleOrNull()
                    ?: throw GradleException("Missing or ambiguous router index for $component; republish with the router module plugin and Gradle Module Metadata.")
                val indexAttributes = attributesOf(artifact.variant)
                val mismatch = codeAttributes.filter { (key, value) -> indexAttributes[key] != value }
                check(mismatch.isEmpty()) { "Router index variant does not match code variant for $component: $mismatch vs $indexAttributes" }
                RouterIndexInput(component.displayName, artifact.file)
            }.sortedBy { it.component }
        }
        // resolvedArtifacts carries values, not producer task dependencies. Retain the artifact
        // FileCollection as well so local producers run before the aggregate consumes their files.
        return RouterIndexArtifacts(local.files + external.files, inputs)
    }

    fun inaccessible(compile: Configuration, runtime: Configuration): Provider<List<String>> =
        compile.incoming.resolutionResult.rootComponent.zip(runtime.incoming.resolutionResult.rootComponent) { c, r ->
            (routerComponents(r).values.map { it.artifactOwner }.toSet() - routerComponents(c).values.map { it.artifactOwner }.toSet())
                .map { it.displayName }.sorted()
        }

    private fun routerComponents(root: ResolvedComponentResult): Map<ComponentIdentifier, RouterCodeComponent> {
        val result = linkedMapOf<ComponentIdentifier, RouterCodeComponent>()
        val visited = mutableSetOf<Pair<ComponentIdentifier, String>>()
        fun visit(component: ResolvedComponentResult, variant: ResolvedVariantResult?) {
            val dependencies = if (variant == null) component.dependencies else component.getDependenciesForVariant(variant)
            dependencies.forEach { dependency ->
                if (dependency is UnresolvedDependencyResult) throw GradleException("Cannot resolve router code graph: ${dependency.attempted}", dependency.failure)
                if (dependency !is ResolvedDependencyResult || dependency.isConstraint) return@forEach
                val resolved = dependency.resolvedVariant
                var artifactVariant = resolved
                while (artifactVariant.externalVariant.isPresent) artifactVariant = artifactVariant.externalVariant.get()
                if (!visited.add(dependency.selected.id to resolved.displayName)) return@forEach
                if (resolved.attributes.getAttribute(marker) != null) {
                    require(resolved.attributes.getAttribute(marker) == 1) { "Unsupported router index schema in ${dependency.selected.id}" }
                    val selection = RouterCodeComponent(artifactVariant.owner, attributesOf(artifactVariant))
                    val previous = result.put(dependency.selected.id, selection)
                    require(previous == null || previous == selection) { "Multiple router code variants selected for ${dependency.selected.id}" }
                }
                visit(dependency.selected, resolved)
            }
        }
        visit(root, null)
        return result
    }

    private fun attributesOf(variant: ResolvedVariantResult): Map<String, String> = variant.attributes.keySet()
        .filter { it.name !in setOf("artifactType", "org.gradle.status", Category.CATEGORY_ATTRIBUTE.name, DocsType.DOCS_TYPE_ATTRIBUTE.name) }
        .associate { it.name to variant.attributes.getAttribute(it).toString() }

    private fun copyAttributes(from: AttributeContainer, to: AttributeContainer) {
        from.keySet().forEach { key ->
            @Suppress("UNCHECKED_CAST")
            key as Attribute<Any>
            from.getAttribute(key)?.let { to.attribute(key, it) }
        }
    }
}
