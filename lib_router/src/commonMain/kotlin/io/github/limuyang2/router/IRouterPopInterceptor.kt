package io.github.limuyang2.router

interface IRouterPopInterceptor {
    suspend fun intercept(chain: Chain): PopResponse

    interface Chain {
        fun request(): PopRequest
        suspend fun proceed(request: PopRequest = request()): PopResponse

        /** 改变页面选择条件时重新匹配目标和拦截器；相同条件沿用已绑定的页面。 */
        suspend fun redirect(builder: PopScope.() -> Unit): PopResponse
    }
}

fun IRouterPopInterceptor.Chain.interrupt(): PopResponse {
    return PopResponse.Interrupted
}
