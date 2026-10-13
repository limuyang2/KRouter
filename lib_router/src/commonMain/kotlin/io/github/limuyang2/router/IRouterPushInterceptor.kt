package io.github.limuyang2.router

/**
 * 可挂起的跳转拦截器
 *
 * Suspending push interceptor.
 */
interface IRouterPushInterceptor {
    /**
     * 检查或改写请求后调用 chain.proceed，或通过 chain.interrupt 中断跳转。
     *
     * Inspects or rewrites a request, then continues with chain.proceed or stops with chain.interrupt.
     */
    suspend fun intercept(chain: Chain): PushResponse

    /**
     * 当前跳转拦截器链上下文
     *
     * Context of the current push interceptor chain.
     */
    interface Chain {
        /**
         * 获取此链阶段的请求
         *
         * Returns the request for this chain stage.
         */
        fun request(): PushRequest
        /**
         * 继续处理请求；改写目标类型时重新执行目标对应的拦截器链。
         *
         * Continues the request; changing the destination class restarts the applicable interceptor chain.
         *
         * @param request 原请求或通过 newBuilder 构建的新请求 / Original request or one rewritten with newBuilder.
         */
        suspend fun proceed(request: PushRequest): PushResponse
    }
}

/**
 * 中断跳转，不修改栈；保留失败与完成回调。
 *
 * Interrupts the push without changing the stack, retaining failure and completion callbacks.
 */
fun IRouterPushInterceptor.Chain.interrupt(): PushResponse {
    val request = request()

    return PushResponse(
        request = request,
        destination = UnknownRoute(),
        routeBeforeCallback = null,
        routeSuccessCallback = null,
        routeFailureCallback = request.routeFailureCallback,
        routeCompletedCallback = request.routeCompletedCallback,
        preventDuplicates = false,
        isCleanStack = false,
        isPopCurrent = false,
        isInterrupted = true
    )
}
