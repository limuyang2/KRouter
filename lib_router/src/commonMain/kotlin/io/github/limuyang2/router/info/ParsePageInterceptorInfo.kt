package io.github.limuyang2.router.info

import io.github.limuyang2.router.IRouterPopInterceptor
import io.github.limuyang2.router.IRouterPushInterceptor

data class ParsePagePushInterceptorInfo(
    val priority: Int,
    val interceptor: IRouterPushInterceptor,
)

data class ParsePagePopInterceptorInfo(
    val priority: Int,
    val interceptor: IRouterPopInterceptor,
)
