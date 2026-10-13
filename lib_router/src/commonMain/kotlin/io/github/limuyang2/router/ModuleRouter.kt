package io.github.limuyang2.router

import androidx.navigation3.runtime.EntryProviderScope
import io.github.limuyang2.router.info.ParseRouterInfo
import kotlin.reflect.KClass

interface ModuleRouter {
    fun routeMasterKV(): Map<KClass<*>, ParseRouterInfo>

    fun routeAliasesKV(): Map<String, KClass<*>>

    fun globalPushInterceptors(): List<GlobalPushInterceptorInfo>

    fun globalPopInterceptors(): List<GlobalPopInterceptorInfo>

    fun entryProviderInstaller(): EntryProviderInstaller
}

typealias EntryProviderInstaller = EntryProviderScope<Any>.() -> Unit
