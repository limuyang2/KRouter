package io.github.limuyang2.router_ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate

internal class OwnerPushInterceptorProcessor(
    val options: Map<String, String>,
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
) {
    fun process(resolver: Resolver): Pair<ModuleFileOwnerPushInterceptorInfo?, List<KSAnnotated>> {
        val routerAnnotationName = "io.github.limuyang2.router.anno.RouterOwnerPushInterceptor"

        logger.info("RouteProcessor: start process: $routerAnnotationName")

        val symbols = resolver.getSymbolsWithAnnotation(routerAnnotationName, false)
        val (valid, invalid) = symbols.partition { it.validate() }
        val validSymbols = valid.filterIsInstance<KSClassDeclaration>()

        val annotationKSType =
            resolver.getClassDeclarationByName(resolver.getKSNameFromString(routerAnnotationName))
                ?.asType(emptyList())

        if (annotationKSType == null) {
            return null to invalid
        }

        return generateCode(validSymbols, annotationKSType) to invalid
    }

    private fun generateCode(
        symbols: List<KSClassDeclaration>,
        annotationKSType: KSType,
    ): ModuleFileOwnerPushInterceptorInfo? {
        val iterator = symbols.iterator()
        if (!iterator.hasNext()) {
            return null
        }

        val ownerPushMap = mutableMapOf<String, MutableList<TempPageInterceptor>>()
        val providerDependenciesKSFile = mutableSetOf<KSFile>()

        while (iterator.hasNext()) {
            val element = iterator.next()
            val targetAnnotation = element.annotations.find {
                if (it.shortName.asString() != "RouterOwnerPushInterceptor") {
                    false
                } else {
                    it.annotationType.resolve() == annotationKSType
                }
            } ?: continue

            val elementQualifiedName = element.qualifiedName?.asString() ?: continue
            if (!element.implementsType("io.github.limuyang2.router.IRouterPushInterceptor")) {
                logger.error(
                    "RouteProcessor: @RouterOwnerPushInterceptor target [$elementQualifiedName] must implement IRouterPushInterceptor.",
                    element
                )
                continue
            }

            val owners = targetAnnotation.arguments.find {
                it.name?.asString() == "owners"
            }?.value.asListOf<KSType>()
            val priorities = targetAnnotation.arguments.find {
                it.name?.asString() == "priorities"
            }?.value.asListOf<Int>()

            if (owners.isEmpty()) {
                logger.error(
                    "RouteProcessor: @RouterOwnerPushInterceptor on [$elementQualifiedName] must declare at least one owner.",
                    element
                )
                continue
            }

            owners.forEachIndexed { index, owner ->
                val ownerDecl = owner.declaration as? KSClassDeclaration ?: return@forEachIndexed
                val ownerQualifiedName =
                    ownerDecl.qualifiedName?.asString() ?: return@forEachIndexed
                ownerPushMap.getOrPut(ownerQualifiedName) { mutableListOf() }.add(
                    TempPageInterceptor(
                        priority = priorities.getOrElse(index) { 10 },
                        clazzQualifiedName = elementQualifiedName,
                        classKind = element.classKind,
                    )
                )
            }

            element.containingFile?.let {
                providerDependenciesKSFile.add(it)
            }
        }

        ownerPushMap.values.forEach { it.sortBy { interceptor -> interceptor.priority } }

        return ModuleFileOwnerPushInterceptorInfo(
            dependencies = providerDependenciesKSFile,
            ownerPushInterceptors = ownerPushMap,
        )
    }

    private fun KSClassDeclaration.implementsType(qualifiedName: String): Boolean {
        return superTypes.any { typeRef ->
            val declaration = typeRef.resolve().declaration
            declaration.qualifiedName?.asString() == qualifiedName ||
                    (declaration as? KSClassDeclaration)?.implementsType(qualifiedName) == true
        }
    }

    private inline fun <reified T> Any?.asListOf(): List<T> {
        return (this as? List<*>)?.filterIsInstance<T>().orEmpty()
    }
}
