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
         * 获取执行本次跳转的 Navigator。全局拦截器是进程级单例、被所有 Navigator 共享，
         * 借此才能区分多 Navigator 场景下当前操作归属哪一个栈。
         *
         * 定位是只读检查（如 backStack、last() 做埋点、去重、门禁判断）；不建议在其中重入
         * push/pop——那会启动另一条拦截器链，与本次操作的先后顺序未定义。
         *
         * Returns the Navigator executing this push. Global interceptors are process-level
         * singletons shared by every Navigator, so this is the only way to tell which stack
         * an operation belongs to when multiple Navigators exist.
         *
         * Intended for read-only inspection (backStack, last() for tracking, dedup or gating);
         * reentrant push/pop is discouraged — it starts another interceptor chain whose
         * ordering against this operation is undefined.
         */
        fun navigator(): Navigator
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
        singleTask = false,
        isInterrupted = true
    )
}
