package io.github.limuyang2.router

import kotlin.reflect.KClass

/**
 * 未匹配或被中断跳转时产生的占位路由。
 *
 * 当请求被拦截器中断或未能解析到对应的路由页面时，使用此对象表示失败目标。
 *
 * Placeholder route produced when navigation is unmatched or interrupted.
 *
 * Represents the target destination when a push request is interrupted by an interceptor or fails to resolve.
 *
 * @param objClazz 尝试打开的目标路由类型，可为 null / Target route class attempted, which may be null.
 * @param uri 尝试打开的目标 URI，可为 null / Target URI attempted, which may be null.
 */
data class UnknownRoute(
    val objClazz: KClass<*>? = null,
    val uri: RouteUri? = null
) : IRouterEntryProvider

/**
 * 导航栈初始占位路由。
 *
 * Initial placeholder entry for the navigation back stack.
 */
internal object InitRoute : IRouterEntryProvider
