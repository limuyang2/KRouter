package io.github.limuyang2.router

class GlobalPushInterceptorInfo(
    val interceptor: IRouterPushInterceptor,
    val priority: Int
)

class GlobalPopInterceptorInfo(
    val interceptor: IRouterPopInterceptor,
    val priority: Int
)
