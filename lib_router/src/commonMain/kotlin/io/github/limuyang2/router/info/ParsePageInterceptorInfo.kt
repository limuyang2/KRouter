package io.github.limuyang2.router.info

import io.github.limuyang2.router.IRouterPopInterceptor
import io.github.limuyang2.router.IRouterPushInterceptor

/**
 * 封装页面绑定的 Push 拦截器及优先级。
 *
 * Encapsulates a page-bound push interceptor along with its priority.
 *
 * @param priority 优先级数值，数值越小越先执行 / Priority value; smaller values run first.
 * @param interceptor 拦截器实例 / Interceptor instance.
 */
data class ParsePagePushInterceptorInfo(
    val priority: Int,
    val interceptor: IRouterPushInterceptor,
)

/**
 * 封装页面绑定的 Pop 拦截器及优先级。
 *
 * Encapsulates a page-bound pop interceptor along with its priority.
 *
 * @param priority 优先级数值，数值越小越先执行 / Priority value; smaller values run first.
 * @param interceptor 拦截器实例 / Interceptor instance.
 */
data class ParsePagePopInterceptorInfo(
    val priority: Int,
    val interceptor: IRouterPopInterceptor,
)
