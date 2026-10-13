package io.github.limuyang2.router.anno

import kotlin.reflect.KClass

/**
 * 将 Push 拦截器外置绑定到一个或多个路由页面。
 *
 * 适用于路由声明位于 commonMain，但拦截器实现位于 androidMain、jvmMain 等平台源集的场景。
 * KSP 会在生成路由表时把这里声明的拦截器合并进对应 owner 的 ParseRouterInfo，运行时不做额外查找。
 *
 * [owners] 声明需要绑定的路由类；[priorities] 与 [owners] 按下标对应，缺失时使用默认优先级 10。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RouterOwnerPushInterceptor(
    val owners: Array<KClass<*>>,
    val priorities: IntArray = [],
)
