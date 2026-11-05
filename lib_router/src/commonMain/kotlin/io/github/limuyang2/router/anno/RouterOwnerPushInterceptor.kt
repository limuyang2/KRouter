package io.github.limuyang2.router.anno

import kotlin.reflect.KClass

/**
 * 将 Push 拦截器外置绑定到一个或多个路由页面。
 *
 * 适用于路由声明位于 commonMain，但拦截器实现位于 androidMain、jvmMain 等平台源集的场景。
 * KSP 会在生成路由表时把这里声明的拦截器合并进对应 owner 的 ParseRouterInfo，运行时不做额外查找。
 *
 * Externally binds a push interceptor to one or more routing destinations.
 *
 * Suitable when routes are declared in commonMain, but interceptor implementations reside in platform
 * source sets such as androidMain or jvmMain. KSP merges interceptors declared here into the matching
 * owner's ParseRouterInfo during route-table generation without runtime lookups.
 *
 * @param owners 需要绑定的目标路由类数组 / Array of target route classes to bind.
 * @param priorities 拦截器优先级数组，与 [owners] 按下标对应，缺失时默认值为 10；数值越小越先执行 / Priority array matching [owners] by index; defaults to 10 when omitted. Smaller values run first.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RouterOwnerPushInterceptor(
    val owners: Array<KClass<*>>,
    val priorities: IntArray = [],
)
