package io.github.limuyang2.router

import kotlin.reflect.KClass

data class UnknownRoute(
    val objClazz: KClass<*>? = null,
    val uri: RouteUri? = null
) : IRouterEntryProvider

object InitRoute : IRouterEntryProvider
