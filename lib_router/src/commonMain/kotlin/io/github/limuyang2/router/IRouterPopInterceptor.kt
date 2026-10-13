package io.github.limuyang2.router

/**
 * 检查已解析目标实例的可挂起返回拦截器
 *
 * Suspending pop interceptor inspecting already-resolved target instances.
 */
interface IRouterPopInterceptor {
    /**
     * 检查请求后放行、重定向或中断；每个链阶段最多调用一次 proceed 或 redirect。
     *
     * Allows, redirects or interrupts the request; each chain stage may call proceed or redirect at most once.
     */
    suspend fun intercept(chain: Chain): PopResponse

    /**
     * 当前返回拦截器链上下文
     *
     * Context of the current pop interceptor chain.
     */
    interface Chain {
        /**
         * 获取绑定具体实例的返回请求
         *
         * Returns the pop request bound to concrete instances.
         */
        fun request(): PopRequest
        /**
         * 放行原请求或 withResult 创建的新请求；不允许借此修改页面选择条件。
         *
         * Continues the original request or a withResult copy; page selection cannot be changed through this method.
         *
         * @param request 同一已绑定计划的请求 / Request belonging to the same bound plan.
         * @throws IllegalStateException 重复推进或请求绑定不匹配 / Repeated advancement or a mismatched bound request.
         */
        suspend fun proceed(request: PopRequest = request()): PopResponse

        /**
         * 改变页面选择条件时重新匹配目标和拦截器；相同条件沿用已绑定的页面。
         *
         * Re-resolves targets and interceptors when selection changes; unchanged selection retains the bound instances.
         *
         * @param builder 新的返回选择及结果配置 / New pop selection and result configuration.
         * @throws IllegalStateException 此阶段已调用过 proceed 或 redirect / This stage has already advanced.
         */
        suspend fun redirect(builder: PopScope.() -> Unit): PopResponse
    }
}

/**
 * 中断本次返回，不移除页面、不投递业务值
 *
 * Interrupts this pop without removing pages or delivering a business value.
 */
fun IRouterPopInterceptor.Chain.interrupt(): PopResponse {
    return PopResponse.Interrupted
}
