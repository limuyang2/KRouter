package io.github.limuyang2.router_ksp.template

import com.google.devtools.ksp.symbol.ClassKind
import io.github.limuyang2.router_ksp.TempEntryInfo
import io.github.limuyang2.router_ksp.TempGlobalInterceptor
import io.github.limuyang2.router_ksp.TempPageInterceptor
import io.github.limuyang2.router_ksp.TempRouteInfo

internal fun createProviderTemplate(
    packageName: String,
    className: String,
    routeInfos: List<TempRouteInfo>?,
    entryInfoList: List<TempEntryInfo>?,
    globalPushInterceptors: List<TempGlobalInterceptor>?,
    globalPopInterceptors: List<TempGlobalInterceptor>?,
): String = """
package $packageName

import kotlin.reflect.KClass
import io.github.limuyang2.router.ModuleRouter
import io.github.limuyang2.router.RouterSingleton
import io.github.limuyang2.router.RouterKeep
import io.github.limuyang2.router.info.ParseRouterInfo
import io.github.limuyang2.router.EntryProviderInstaller
import io.github.limuyang2.router.GlobalPushInterceptorInfo
import io.github.limuyang2.router.GlobalPopInterceptorInfo

/**
 * 路由类的提供者，由 KSP 自动生成，请勿修改
 */
@RouterKeep
class $className : ModuleRouter {
    
    /**
     * 获取主要的路由kv映射关系
     */
    override fun routeMasterKV(): Map<KClass<*>, ParseRouterInfo> {
        ${
    if (routeInfos.isNullOrEmpty()) {
        "return emptyMap()"
    } else {
        """return mapOf(
            ${routeInfos.contentMasterMap()}
        )"""
    }
}
    }

    /**
     * 获取路由别名的kv映射关系
     */
    override fun routeAliasesKV(): Map<String, KClass<*>> {
        ${
    if (routeInfos.isNullOrEmpty()) {
        "return emptyMap()"
    } else {
        """return mapOf(
            ${routeInfos.contentAliasesMap()}
        )"""
    }
}
    }
    
    /**
     * 获取全局push拦截器列表
     */
    override fun globalPushInterceptors(): List<GlobalPushInterceptorInfo> {
        ${
    if (globalPushInterceptors.isNullOrEmpty()) {
        "return emptyList()"
    } else {
        """return listOf(
            ${globalPushInterceptors.interceptorString("GlobalPushInterceptorInfo", "io.github.limuyang2.router.resolvePushInterceptor")}
        )"""
    }
}
    }

    /**
     * 获取全局pop拦截器列表
     */
    override fun globalPopInterceptors(): List<GlobalPopInterceptorInfo> {
        ${
    if (globalPopInterceptors.isNullOrEmpty()) {
        "return emptyList()"
    } else {
        """return listOf(
            ${globalPopInterceptors.interceptorString("GlobalPopInterceptorInfo", "io.github.limuyang2.router.resolvePopInterceptor")}
        )"""
    }
}
    }
    
    
    /**
     * 获取模块的页面入口提供者
     */
    override fun entryProviderInstaller() : EntryProviderInstaller = {
        ${
    if (entryInfoList.isNullOrEmpty()) {
        ""
    } else {
        entryInfoList.entryString()
    }
}
    }
}

""".trim()


private fun List<TempRouteInfo>.contentMasterMap(): String {
    return this.joinToString(separator = ", ") {
        val url = it.resolvedUrlExpression


        """
            ${it.clazzQualifiedName}::class to ParseRouterInfo(
                url = $url,
                scheme = ${if (it.scheme.isNotBlank()) "\"${it.scheme}\"" else "RouterSingleton.getConfig().defaultScheme"},
                host = "${it.host}",
                path = "${it.path}",
                desc = "${it.desc}",
                pushInterceptors = ${
            if (it.pushInterceptors.isNotEmpty()) "listOf(${
                it.pushInterceptors.joinToString(
                    separator = ", "
                ) { interceptor ->
                    val instantiation = interceptor.instantiationCode("io.github.limuyang2.router.resolvePushInterceptor")
                    "io.github.limuyang2.router.info.ParsePagePushInterceptorInfo(priority = ${interceptor.priority}, interceptor = $instantiation)"
                }
            })" else "emptyList()"
        },
                popInterceptors = ${
            if (it.popInterceptors.isNotEmpty()) "listOf(${
                it.popInterceptors.joinToString(
                    separator = ", "
                ) { interceptor ->
                    val instantiation = interceptor.instantiationCode("io.github.limuyang2.router.resolvePopInterceptor")
                    "io.github.limuyang2.router.info.ParsePagePopInterceptorInfo(priority = ${interceptor.priority}, interceptor = $instantiation)"
                }
            })" else "emptyList()"
        },
                instanceProvider = ${it.instanceProviderString},
                singleTask = ${it.singleTask}
            )
        """.trim()
    }
}

private fun List<TempRouteInfo>.contentAliasesMap(): String {
    return this.joinToString(separator = ", ") {
        val url = it.resolvedUrlExpression

        """
            $url to ${it.clazzQualifiedName}::class
        """.trimEnd()
    }.trimStart()
}



private fun List<TempGlobalInterceptor>.interceptorString(infoClassName: String, resolveFunction: String): String {
    return this.joinToString(separator = ", \n        ") {
        "$infoClassName(interceptor = ${it.instantiationCode(resolveFunction)}, priority = ${it.priority})"
    }
}

/**
 * 拦截器实例化代码三态：object 直接引用；无参构造类包 resolver 加兜底；
 * DI-only 类（无无参构造）仅 resolver，运行时必须命中 interceptorResolver。
 */
private fun interceptorInstantiationCode(
    clazzQualifiedName: String,
    classKind: ClassKind,
    noArgConstruction: Boolean,
    resolveFunction: String,
): String {
    return when {
        classKind == ClassKind.OBJECT -> clazzQualifiedName
        noArgConstruction -> "$resolveFunction(${clazzQualifiedName}::class) { $clazzQualifiedName() }"
        else -> "$resolveFunction(${clazzQualifiedName}::class)"
    }
}

private fun TempGlobalInterceptor.instantiationCode(resolveFunction: String) =
    interceptorInstantiationCode(clazzQualifiedName, classKind, noArgConstruction, resolveFunction)

private fun TempPageInterceptor.instantiationCode(resolveFunction: String) =
    interceptorInstantiationCode(clazzQualifiedName, classKind, noArgConstruction, resolveFunction)

private fun List<TempEntryInfo>.entryString(): String {
    return this.joinToString(separator = "\n        ") {
        """entry<${it.clazzQualifiedName}>(clazzContentKey = { it.contentKey() }, metadata = { it.metadata() }) { it.UI() }"""
    }
}

/**
 * 生成模块聚合注册入口。
 *
 * 每个 KSP compilation 生成一个 object，内部调用 registerModuleRouters
 * 把本模块的 [ModuleRouterProvider] 注册到 RouterSingleton。
 * JSON 索引声明此入口，宿主 Gradle 插件按实际依赖选择索引并统一调用 registerAll()。
 */
internal fun createRegistryTemplate(
    packageName: String,
    registryName: String,
    providerName: String,
): String = """
package $packageName

import io.github.limuyang2.router.RouterKeep
import io.github.limuyang2.router.registerModuleRouters

/**
 * 路由模块聚合注册入口，由 KSP 自动生成，请勿修改。
 * 由路由索引声明，供宿主 Gradle 聚合插件统一调用。
 */
@RouterKeep
object $registryName {
    fun registerAll() {
        registerModuleRouters($providerName())
    }
}
""".trim()
