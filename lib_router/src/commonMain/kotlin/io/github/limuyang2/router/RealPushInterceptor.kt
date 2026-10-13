package io.github.limuyang2.router

internal class RealPushInterceptor(private val core: Core) : IRouterPushInterceptor {
    override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
        val request = chain.request()

        return PushResponse(
            request,
            destination = core.getClassInstance(request),
            routeBeforeCallback = request.routeBeforeCallback,
            routeSuccessCallback = request.routeSuccessCallback,
            routeFailureCallback = request.routeFailureCallback,
            routeCompletedCallback = request.routeCompletedCallback,
            preventDuplicates = request.preventDuplicates,
            isCleanStack = request.isCleanStack,
            isPopCurrent = request.isPopCurrent,
        )
    }
}

internal class RealPushInterceptorChain(
    private val interceptors: List<IRouterPushInterceptor>,
    private val index: Int,
    private val request: PushRequest,
    private val core: Core,
    private val onRestart: (PushRequest) -> Unit,
) : IRouterPushInterceptor.Chain {

    private val clazz = if (this.request.destination != null) {
        this.request.destination::class
    } else if (this.request.uri != null) {
        core.findClassByClearUri(this.request.uri)
    } else {
        null
    }

    override fun request(): PushRequest = request

    override suspend fun proceed(request: PushRequest): PushResponse {
        if (index > 0) {
            val newClazz = if (request.destination != null) {
                request.destination::class
            } else if (request.uri != null) {
                core.findClassByClearUri(request.uri)
            } else {
                null
            }

            if (newClazz != clazz) {
                onRestart(request)
                return interrupt()
            }
        }

        check(index < interceptors.size)
        val next = copy(index = index + 1, request = request)
        val interceptor = interceptors[index]
        return interceptor.intercept(next)
    }

    private fun copy(
        index: Int = this.index,
        request: PushRequest = this.request,
    ) = RealPushInterceptorChain(interceptors, index, request, core, onRestart)
}
