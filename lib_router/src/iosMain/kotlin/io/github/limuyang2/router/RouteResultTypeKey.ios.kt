package io.github.limuyang2.router

import kotlin.reflect.KClass

internal actual fun KClass<*>.routeResultTypeKey(): String = qualifiedName ?: "any"
