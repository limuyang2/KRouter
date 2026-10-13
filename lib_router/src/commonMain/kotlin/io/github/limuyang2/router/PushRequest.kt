package io.github.limuyang2.router

/**
 * Push 拦截器读取的不可变请求；originalUri 保留原输入，uri 是不含查询参数的匹配地址。
 *
 * Immutable request inspected by push interceptors; originalUri retains the input and uri is the query-free match URI.
 */
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
    /**
     * 创建保留当前配置的新构建器，不修改原请求
     *
     * Creates a builder retaining current settings without mutating this request.
     */
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

    /**
     * 拦截器中的请求改写构建器
     *
     * Request-rewriting builder used by interceptors.
     */
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

        /**
         * 设置成功跳转后仅保留新页面
         *
         * Sets whether a successful push retains only the new page.
         */
        fun cleanStack(isClean: Boolean = true) = this.apply { this.isCleanStack = isClean }

        /**
         * push 成功后关闭旧 current。
         *
         * 这里只表达页面生命周期变化，不表达 replace 语义；
         * 新页面不会自动接管旧页面的任何结果关系。
         *
         * Closes the previous current page after a successful push; the new page inherits no result relationships.
         */
        fun popCurrent(isPop: Boolean = true) = this.apply { this.isPopCurrent = isPop }

        /**
         * 控制栈顶相同类型去重；同一对象重复入栈始终被拒绝。
         *
         * Controls same-class duplicate prevention at the top; pushing the same object twice is always rejected.
         */
        fun preventDuplicates(prevent: Boolean = true) = this.apply { this.preventDuplicates = prevent }

        /**
         * 设置实际入栈前调用的回调
         *
         * Sets the callback invoked before the actual stack insertion.
         */
        fun onRouteBefore(callback: () -> Unit) = this.apply { this.routeBeforeCallback = callback }

        /**
         * 设置成功入栈后的回调，不是结果回传接收器
         *
         * Sets the successful-push callback, not a returned-result consumer.
         */
        fun onRouteSuccess(callback: () -> Unit) = this.apply { this.routeSuccessCallback = callback }

        /**
         * 设置匹配 URI 的协议部分；保留原始输入 URI
         *
         * Sets the match URI scheme while retaining the original input URI.
         */
        fun schema(schema: String) = this.apply { this.schema = schema }

        /**
         * 设置匹配 URI 的主机部分
         *
         * Sets the host component of the match URI.
         */
        fun host(host: String) = this.apply { this.host = host }

        /**
         * 设置匹配 URI 的路径部分
         *
         * Sets the path component of the match URI.
         */
        fun path(path: String) = this.apply { this.path = path }

        /**
         * 设置完整 URL 并清除已设置的协议、主机、路径覆写；build() 时重新解析查询参数。
         *
         * Sets a complete URL and clears scheme, host and path overrides; build() reparses query parameters.
         */
        fun url(url: String) = this.apply {
            this.url = url
            this.schema = null
            this.host = null
            this.path = null
        }

        /**
         * 替换额外参数集合；使用完整 URL 重建时，同名 extras 优先于 URL 查询参数。
         *
         * Replaces extras; when rebuilding from a complete URL, extras override query parameters with the same name.
         */
        fun extras(extras: Map<String, Any>) = this.apply { this.extras = extras }

        /**
         * 新增或替换单个参数，保留其他参数
         *
         * Adds or replaces one extra while retaining other extras.
         */
        fun setExtra(key: String, value: Any) = this.apply {
            val map = this.extras?.toMutableMap() ?: mutableMapOf()
            map[key] = value
            this.extras = map
        }

        /**
         * 删除 extras 中的指定参数；完整 URL 中的同名查询参数可能在 build() 时重新加入。
         *
         * Removes an extra; a query parameter with the same name may be restored when build() parses a complete URL.
         */
        fun removeExtra(key: String) = this.apply {
            this.extras = this.extras?.toMutableMap()?.also { it.remove(key) }
        }

        /**
         * 设置实例目标并清除当前匹配 URI；已有完整 URL 或 URI 部分覆写仍优先参与 build()。
         *
         * Sets an instance target and clears the current match URI; a configured complete URL or URI override
         * still takes precedence during build().
         */
        fun destination(destination: IRouterEntryProvider) = this.apply {
            this.destination = destination
            this.uri = null
        }

        /**
         * 构建新请求；完整 URL 优先，其次是 URI 部分覆写，最后是已有实例或 URI。
         *
         * Builds a new request, prioritizing a complete URL, then URI component overrides, then an existing instance or URI.
         *
         * @return 改写后的不可变请求 / Rewritten immutable request.
         * @throws RouterException URL 解析或 URI 构建失败，或没有设置目标 / Invalid URL/URI or missing destination.
         */
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
