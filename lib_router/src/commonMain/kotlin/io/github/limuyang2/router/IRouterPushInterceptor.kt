package io.github.limuyang2.router

interface IRouterPushInterceptor {
    suspend fun intercept(chain: Chain): PushResponse

    interface Chain {
        fun request(): PushRequest
        suspend fun proceed(request: PushRequest): PushResponse
    }
}

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
