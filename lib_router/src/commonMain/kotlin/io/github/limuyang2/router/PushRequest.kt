package io.github.limuyang2.router

class PushRequest internal constructor(
    val originalUri: RouteUri?,
    val uri: RouteUri?,
    val extras: Map<String, Any>?,
    val destination: IRouterEntryProvider?,
    val routeBeforeCallback: (() -> Unit)?,
    val routeSuccessCallback: (() -> Unit)?,
    val routeFailureCallback: (() -> Unit)?,
    val routeCompletedCallback: (() -> Unit)?,
    val preventDuplicates: Boolean,
    val isCleanStack: Boolean,
    val isPopCurrent: Boolean,
) {
    fun newBuilder(): Builder {
        return Builder(this)
    }

    private fun copy(
        originalUri: RouteUri? = this.originalUri,
        uri: RouteUri? = this.uri,
        extras: Map<String, Any>? = this.extras,
        destination: IRouterEntryProvider? = this.destination,
        routeBeforeCallback: (() -> Unit)? = this.routeBeforeCallback,
        routeSuccessCallback: (() -> Unit)? = this.routeSuccessCallback,
        routeFailureCallback: (() -> Unit)? = this.routeFailureCallback,
        routeCompletedCallback: (() -> Unit)? = this.routeCompletedCallback,
        preventDuplicates: Boolean = this.preventDuplicates,
        isCleanStack: Boolean = this.isCleanStack,
        isPopCurrent: Boolean = this.isPopCurrent,
    ): PushRequest {
        return PushRequest(
            originalUri,
            uri,
            extras,
            destination,
            routeBeforeCallback = routeBeforeCallback,
            routeSuccessCallback = routeSuccessCallback,
            routeFailureCallback = routeFailureCallback,
            routeCompletedCallback = routeCompletedCallback,
            preventDuplicates = preventDuplicates,
            isCleanStack = isCleanStack,
            isPopCurrent = isPopCurrent
        )
    }

    class Builder internal constructor(private val request: PushRequest) {
        private var destination: IRouterEntryProvider? = request.destination
        private var extras: Map<String, Any>? = request.extras
        private var schema: String? = null
        private var host: String? = null
        private var path: String? = null
        private var url: String? = null
        private var uri: RouteUri? = request.uri
        private var routeBeforeCallback: (() -> Unit)? = request.routeBeforeCallback
        private var routeSuccessCallback: (() -> Unit)? = request.routeSuccessCallback
        private var preventDuplicates: Boolean = request.preventDuplicates
        private var isCleanStack: Boolean = request.isCleanStack
        private var isPopCurrent: Boolean = request.isPopCurrent

        fun cleanStack(isClean: Boolean = true) = this.apply { this.isCleanStack = isClean }

        /**
         * push 成功后关闭旧 current。
         *
         * 这里只表达页面生命周期变化，不表达 replace 语义；
         * 新页面不会自动接管旧页面的任何结果关系。
         */
        fun popCurrent(isPop: Boolean = true) = this.apply { this.isPopCurrent = isPop }

        fun preventDuplicates(prevent: Boolean = true) = this.apply { this.preventDuplicates = prevent }

        fun onRouteBefore(callback: () -> Unit) = this.apply { this.routeBeforeCallback = callback }

        fun onRouteSuccess(callback: () -> Unit) = this.apply { this.routeSuccessCallback = callback }

        fun schema(schema: String) = this.apply { this.schema = schema }

        fun host(host: String) = this.apply { this.host = host }

        fun path(path: String) = this.apply { this.path = path }

        fun url(url: String) = this.apply {
            this.url = url
            this.schema = null
            this.host = null
            this.path = null
        }

        fun extras(extras: Map<String, Any>) = this.apply { this.extras = extras }

        fun setExtra(key: String, value: Any) = this.apply {
            val map = this.extras?.toMutableMap() ?: mutableMapOf()
            map[key] = value
            this.extras = map
        }

        fun removeExtra(key: String) = this.apply {
            this.extras = this.extras?.toMutableMap()?.also { it.remove(key) }
        }

        fun destination(destination: IRouterEntryProvider) = this.apply {
            this.destination = destination
            this.uri = null
        }

        fun build(): PushRequest {
            val url = url
            if (!url.isNullOrEmpty()) {
                val uri = url.toUriOrNull() ?: throw RouterException("url 格式错误")
                val cleanUri = uri.buildUpon().clearQuery().build() ?: throw RouterException("url 格式错误")
                val mergedExtras = uri.getUriParamsMap() + (extras ?: emptyMap())

                return request.copy(
                    originalUri = uri,
                    uri = cleanUri,
                    extras = mergedExtras,
                    destination = null,
                    routeBeforeCallback = routeBeforeCallback,
                    routeSuccessCallback = routeSuccessCallback,
                    preventDuplicates = preventDuplicates,
                    isCleanStack = isCleanStack,
                    isPopCurrent = isPopCurrent
                )
            } else if (schema != null || host != null || path != null) {
                val uri = (request.uri?.buildUpon() ?: RouteUri.Builder())
                    .scheme(schema ?: request.uri?.scheme)
                    .authority(host ?: request.uri?.host)
                    .path(path ?: request.uri?.path)
                    .build() ?: throw RouterException("uri 构建失败")

                return request.copy(
                    uri = uri,
                    extras = extras,
                    destination = null,
                    routeBeforeCallback = routeBeforeCallback,
                    routeSuccessCallback = routeSuccessCallback,
                    preventDuplicates = preventDuplicates,
                    isCleanStack = isCleanStack,
                    isPopCurrent = isPopCurrent
                )
            } else {
                if (destination == null && uri == null) {
                    throw RouterException("请设置 destination、uri 任意一种")
                }
                return request.copy(
                    uri = uri,
                    extras = extras,
                    destination = destination,
                    routeBeforeCallback = routeBeforeCallback,
                    routeSuccessCallback = routeSuccessCallback,
                    preventDuplicates = preventDuplicates,
                    isCleanStack = isCleanStack,
                    isPopCurrent = isPopCurrent
                )
            }
        }
    }
}
