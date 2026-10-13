package io.github.limuyang2.router.anno

import kotlin.reflect.KClass

@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Router(
    val path: String = "",
    val scheme: String = "",
    val host: String = "",
    val desc: String = "",
    val pushInterceptors: Array<KClass<*>> = [],
    val pushInterceptorPriorities: IntArray = [],
    val popInterceptors: Array<KClass<*>> = [],
    val popInterceptorPriorities: IntArray = [],
)
