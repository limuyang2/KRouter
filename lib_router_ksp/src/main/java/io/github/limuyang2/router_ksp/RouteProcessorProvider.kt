package io.github.limuyang2.router_ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFile
import io.github.limuyang2.router.index.RouterIndex
import io.github.limuyang2.router_ksp.template.createProviderTemplate
import io.github.limuyang2.router_ksp.template.createRegistryTemplate
import java.io.OutputStream

/**
 * KSP 处理器入口。
 *
 * 每个业务模块启用 KSP 后，会生成两份代码和一份版本化 JSON 索引：
 * 1. `ModuleRouterProvider_*`：实现 ModuleRouter，承载本模块的路由表/拦截器/Entry。
 * 2. `ModuleRouterRegistry_*`：一个带 `registerAll()` 的 object，由 JSON 索引声明，供宿主模块的 Gradle 插件聚合。
 *
 * 宿主模块通过聚合生成的 `RouterAggregatedRegistry.registerAll()` 统一注册所有模块，
 * 不再依赖 JVM-only 的 ServiceLoader，Android/Desktop/iOS 三端用法一致。
 */
class RouteProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return Processor(
            options = environment.options,
            codeGenerator = environment.codeGenerator,
            logger = environment.logger
        )
    }
}


private class Processor(
    val options: Map<String, String>,
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
) : SymbolProcessor {

    private companion object {
        const val REGISTRY_PACKAGE_OPTION = "router.registryPackage"
        const val DEPRECATED_REGISTRY_NAME_OPTION = "router.registryName"
        const val DEFAULT_REGISTRY_PACKAGE = "io.github.limuyang2.router.generated"
        const val MODULE_PROVIDER_NAME = "ModuleRouterProvider"
        const val MODULE_REGISTRY_NAME = "ModuleRouterRegistry"
    }

    private val registryPackage = (options[REGISTRY_PACKAGE_OPTION]?.takeIf { it.isNotBlank() }
        ?: DEFAULT_REGISTRY_PACKAGE).also(RouterIndex::requireQualifiedName)

    init {
        if (options[DEPRECATED_REGISTRY_NAME_OPTION]?.isNotBlank() == true) {
            logger.warn("$DEPRECATED_REGISTRY_NAME_OPTION is ignored. Router KSP automatically names module registries.")
        }
    }

    // 作为成员变量，跨 process() 轮次复用，保持 generatedInstanceProviders 去重集合有效
    private val routeProcessor = RouteProcessor(options, codeGenerator, logger)
    private val globalPushInterceptorProcessor = GlobalPushInterceptorProcessor(options, codeGenerator, logger)
    private val globalPopInterceptorProcessor = GlobalPopInterceptorProcessor(options, codeGenerator, logger)
    private val ownerPushInterceptorProcessor = OwnerPushInterceptorProcessor(options, codeGenerator, logger)
    private val ownerPopInterceptorProcessor = OwnerPopInterceptorProcessor(options, codeGenerator, logger)

    private val moduleId = requireNotNull(options["router.moduleId"]) {
        "Missing router.moduleId. Apply io.github.limuyang2.router.module (or the host plugin) to supply it automatically."
    }.also { RouterIndex(it, emptyList()) }
    private val suffix = RouterIndex(moduleId, emptyList()).suffix
    private val indexResourcePath = RouterIndex(moduleId, emptyList()).resourcePath
    private val providerName = "${MODULE_PROVIDER_NAME}_$suffix"
    private val registryName = "${MODULE_REGISTRY_NAME}_$suffix"
    private val routes = linkedMapOf<String, TempRouteInfo>()
    private val entries = linkedMapOf<String, TempEntryInfo>()
    private val globalPush = linkedMapOf<String, TempGlobalInterceptor>()
    private val globalPop = linkedMapOf<String, TempGlobalInterceptor>()
    private val ownerPush = linkedMapOf<String, List<TempPageInterceptor>>()
    private val ownerPop = linkedMapOf<String, List<TempPageInterceptor>>()
    private var deferredCount = 0

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Only immutable models survive rounds. Associate sources while KSFile symbols are still
        // valid; CodeGenerator records paths immediately, even before the final output is created.
        val deferred = mutableListOf<KSAnnotated>()
        val (routeResult, pendingRoutes) = routeProcessor.process(resolver)
        deferred.addAll(pendingRoutes)
        routeResult?.tempRouteInfoList?.forEach { routes[it.clazzQualifiedName] = it }
        routeResult?.tempEntryInfoList?.forEach { entries[it.clazzQualifiedName] = it }

        val (pushResult, pendingPush) = globalPushInterceptorProcessor.process(resolver)
        deferred.addAll(pendingPush)
        pushResult?.tempPushList?.forEach { globalPush[it.clazzQualifiedName] = it }
        val (popResult, pendingPop) = globalPopInterceptorProcessor.process(resolver)
        deferred.addAll(pendingPop)
        popResult?.tempPopList?.forEach { globalPop[it.clazzQualifiedName] = it }
        val (ownerPushResult, pendingOwnerPush) = ownerPushInterceptorProcessor.process(resolver)
        deferred.addAll(pendingOwnerPush)
        ownerPushResult?.ownerPushInterceptors?.forEach { (owner, values) ->
            ownerPush[owner] = (ownerPush[owner].orEmpty() + values).distinctBy { it.clazzQualifiedName }
        }
        val (ownerPopResult, pendingOwnerPop) = ownerPopInterceptorProcessor.process(resolver)
        deferred.addAll(pendingOwnerPop)
        ownerPopResult?.ownerPopInterceptors?.forEach { (owner, values) ->
            ownerPop[owner] = (ownerPop[owner].orEmpty() + values).distinctBy { it.clazzQualifiedName }
        }
        // Associate only roots that contributed router data in this round. KSP traces their
        // referenced types; aggregating outputs still invalidate when new routes are introduced.
        val sources = buildSet {
            addAll(routeResult?.dependencies.orEmpty())
            addAll(pushResult?.dependencies.orEmpty())
            addAll(popResult?.dependencies.orEmpty())
            addAll(ownerPushResult?.dependencies.orEmpty())
            addAll(ownerPopResult?.dependencies.orEmpty())
        }.toList()
        codeGenerator.associateByPath(sources, indexResourcePath.removeSuffix(".json"), "json")
        if (hasRegistry()) {
            codeGenerator.associate(sources, registryPackage, providerName)
            codeGenerator.associate(sources, registryPackage, registryName)
        }
        deferredCount = deferred.size
        return deferred
    }

    override fun finish() {
        if (deferredCount != 0) {
            logger.error("Router processing ended with $deferredCount unresolved symbols in $moduleId")
            return
        }
        val duplicate = routes.values.groupBy { it.resolvedUrlExpression }.values.firstOrNull { it.size > 1 }
        if (duplicate != null) {
            logger.error("Duplicate route in $moduleId: ${duplicate.joinToString { it.clazzQualifiedName }}")
            return
        }
        val dependencies = Dependencies(aggregating = true)
        val hasRegistry = hasRegistry()
        if (hasRegistry) {
            codeGenerator.createNewFile(dependencies, registryPackage, providerName).use {
                it.appendText(createProviderTemplate(
                    packageName = registryPackage,
                    className = providerName,
                    routeInfos = routes.values.sortedBy { it.clazzQualifiedName }.mergeOwnerInterceptors(ownerPush, ownerPop),
                    entryInfoList = entries.values.sortedBy { it.clazzQualifiedName },
                    globalPushInterceptors = globalPush.values.sortedWith(compareBy({ it.priority }, { it.clazzQualifiedName })),
                    globalPopInterceptors = globalPop.values.sortedWith(compareBy({ it.priority }, { it.clazzQualifiedName })),
                ))
            }
            codeGenerator.createNewFile(dependencies, registryPackage, registryName).use {
                it.appendText(createRegistryTemplate(registryPackage, registryName, providerName))
            }
        }
        val index = RouterIndex(moduleId, if (hasRegistry) listOf("$registryPackage.$registryName") else emptyList())
        codeGenerator.createNewFileByPath(
            dependencies, indexResourcePath.removeSuffix(".json"), "json",
        ).use { it.appendText(index.encode()) }
        logger.info("Generated router index for $moduleId (${index.registries.size} registry)")
    }

    private fun hasRegistry(): Boolean = routes.isNotEmpty() || entries.isNotEmpty() || globalPush.isNotEmpty() || globalPop.isNotEmpty()

    private fun List<TempRouteInfo>.mergeOwnerInterceptors(
        ownerPushInterceptors: Map<String, List<TempPageInterceptor>>,
        ownerPopInterceptors: Map<String, List<TempPageInterceptor>>,
    ): List<TempRouteInfo> {
        if (ownerPushInterceptors.isEmpty() && ownerPopInterceptors.isEmpty()) {
            return this
        }

        return map { routeInfo ->
            val pushInterceptors = (routeInfo.pushInterceptors + ownerPushInterceptors[routeInfo.clazzQualifiedName].orEmpty())
                .sortedWith(compareBy({ it.priority }, { it.clazzQualifiedName }))
            val popInterceptors = (routeInfo.popInterceptors + ownerPopInterceptors[routeInfo.clazzQualifiedName].orEmpty())
                .sortedWith(compareBy({ it.priority }, { it.clazzQualifiedName }))

            routeInfo.copy(
                pushInterceptors = pushInterceptors,
                popInterceptors = popInterceptors,
            )
        }
    }
}


internal class ModuleRouterProviderFileRouterInfo(
    val dependencies: Set<KSFile>,
    val packageName: String,
    val tempRouteInfoList: List<TempRouteInfo>,
    val tempEntryInfoList: List<TempEntryInfo>,
)

internal class ModuleFileGlobalPushInterceptorInfo(
    val dependencies: Set<KSFile>,
    val packageName: String,
    val tempPushList: List<TempGlobalInterceptor>,
)

internal class ModuleFileGlobalPopInterceptorInfo(
    val dependencies: Set<KSFile>,
    val packageName: String,
    val tempPopList: List<TempGlobalInterceptor>,
)

internal class ModuleFileOwnerPushInterceptorInfo(
    val dependencies: Set<KSFile>,
    val ownerPushInterceptors: Map<String, List<TempPageInterceptor>>,
)

internal class ModuleFileOwnerPopInterceptorInfo(
    val dependencies: Set<KSFile>,
    val ownerPopInterceptors: Map<String, List<TempPageInterceptor>>,
)


internal data class TempGlobalInterceptor(
    /**
     * 优先级, 值越小优先级越高
     */
    val priority: Int = 0,
    val clazzSimpleName: String,
    val clazzPackageName: String,
    val clazzQualifiedName: String,
    val classKind: ClassKind,
)


internal fun OutputStream.appendText(str: String) {
    write(str.toByteArray())
}
