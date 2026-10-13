package io.github.limuyang2.router

import kotlin.reflect.KClass

// JS has no qualifiedName. Class identity keeps equally named types in separate result slots.
private val resultTypeKeys = mutableMapOf<KClass<*>, String>()

internal actual fun KClass<*>.routeResultTypeKey(): String =
    resultTypeKeys.getOrPut(this) { "web-type-${resultTypeKeys.size}" }
