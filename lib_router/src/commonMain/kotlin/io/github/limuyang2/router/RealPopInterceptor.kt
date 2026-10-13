package io.github.limuyang2.router

internal class RealPopInterceptorChain(
    private val interceptors: List<IRouterPopInterceptor>,
    private val index: Int,
    private val request: PopRequest,
) : IRouterPopInterceptor.Chain {
    private var advanced = false

    override fun request(): PopRequest = request

    override suspend fun proceed(request: PopRequest): PopResponse {
        check(!advanced) { "A Pop interceptor may proceed or redirect only once." }
        check(request.plan === this.request.plan && request.popType.sameSelectionAs(this.request.popType)) {
            "Use redirect to change the Pop target; proceed only accepts the current request or withResult()."
        }
        advanced = true
        return proceedNext(request)
    }

    override suspend fun redirect(builder: PopScope.() -> Unit): PopResponse {
        check(!advanced) { "A Pop interceptor may proceed or redirect only once." }
        advanced = true
        val popType = PopScope().apply(builder).popType
        return if (popType.sameSelectionAs(request.popType)) {
            proceedNext(PopRequest(popType, request.plan))
        } else {
            PopResponse.Redirected(request, popType)
        }
    }

    private suspend fun proceedNext(request: PopRequest): PopResponse {
        if (!request.plan.isValid) return PopResponse.Interrupted
        if (index == interceptors.size) return PopResponse.Allowed(request)
        val next = RealPopInterceptorChain(interceptors, index + 1, request)
        return interceptors[index].intercept(next)
    }
}
