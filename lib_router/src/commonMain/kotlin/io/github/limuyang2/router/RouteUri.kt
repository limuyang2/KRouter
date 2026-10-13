package io.github.limuyang2.router

/**
 * 跨平台 URI 抽象，提供 scheme / host / path / query 的解析能力。
 * 各平台通过 expect/actual 对接 Uri (Android) / NSURLComponents (iOS) / java.net.URI (JVM)，
 * JS 和 Wasm 共用 Kotlin URI 解析实现。
 *
 * Cross-platform URI abstraction for scheme, host, path and query. Platform implementations use
 * Android Uri, iOS NSURLComponents and JVM java.net.URI; JS and Wasm share a Kotlin URI parser.
 */
expect class RouteUri {

    /**
     * URI 的协议部分，如 https、test。
     *
     * URI scheme, for example https or test.
     */
    val scheme: String?

    /**
     * URI 的主机部分，如 example.com。
     *
     * URI host, for example example.com.
     */
    val host: String?

    /**
     * URI 的路径部分，如 /a/b。
     *
     * URI path, for example /a/b.
     */
    val path: String?

    /**
     * URI 的查询字符串部分，不包含问号。
     *
     * URI query string without the leading question mark.
     */
    val query: String?

    /**
     * 获取指定 key 的第一个 query 参数值。
     *
     * Returns the first query-parameter value for the key, or null when absent.
     *
     * @param key 参数名称 / Parameter name.
     */
    fun getQueryParameter(key: String): String?

    /**
     * 获取所有 query 参数的 key 集合。
     *
     * Returns the set of all query-parameter names.
     */
    fun getQueryParameterNames(): Set<String>

    /**
     * 获取指定 key 的所有 query 参数值列表。
     *
     * Returns all query-parameter values for the key, or an empty list when absent.
     *
     * @param key 参数名称 / Parameter name.
     */
    fun getQueryParameters(key: String): List<String>

    /**
     * 基于当前 URI 创建一个新的 Builder。
     *
     * Creates a builder initialized from this URI without changing the original.
     */
    fun buildUpon(): Builder

    /**
     * 返回 URI 的字符串表示。
     *
     * Returns the string representation of this URI.
     */
    override fun toString(): String

    /**
     * URI 构建器，用于链式构造 RouteUri 实例。
     *
     * Builder for constructing RouteUri instances through chained operations.
     */
    class Builder {

        /**
         * 创建一个空的 Builder。
         *
         * Creates an empty builder.
         */
        constructor()

        /**
         * 设置 URI 的协议部分。
         *
         * Sets the URI scheme.
         *
         * @param scheme 协议，可为 null / Scheme, which may be null.
         */
        fun scheme(scheme: String?): Builder

        /**
         * 设置 URI 的授权部分（host:port）。
         *
         * Sets the URI authority, including host and optional port.
         *
         * @param authority 授权部分，可为 null / Authority, which may be null.
         */
        fun authority(authority: String?): Builder

        /**
         * 设置 URI 的路径部分。
         *
         * Sets the URI path.
         *
         * @param path 路径，可为 null / Path, which may be null.
         */
        fun path(path: String?): Builder

        /**
         * 清除所有已添加的 query 参数。
         *
         * Removes all query parameters from the builder.
         */
        fun clearQuery(): Builder

        /**
         * 追加一个 query 参数键值对。
         *
         * Appends a query-parameter name and value without replacing previous entries of the same name.
         *
         * @param key 参数名称 / Parameter name.
         * @param value 参数值，可为 null / Parameter value, which may be null.
         */
        fun appendQueryParameter(key: String, value: String?): Builder

        /**
         * 构建并返回 RouteUri，构建失败时返回 null。
         *
         * Builds a RouteUri, returning null when construction fails.
         */
        fun build(): RouteUri?
    }
}

/**
 * 将字符串转换为 RouteUri，解析失败返回 null。
 */
internal expect fun String.toUriOrNull(): RouteUri?

/**
 * 合并 URI query 参数和额外 extras，同名 extras 会覆盖 query 参数。
 */
internal fun RouteUri.getUriParamsMap(
    extras: Map<String, Any>? = null,
): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    try {
        getQueryParameterNames().forEach { key ->
            map[key] = getQueryParameters(key)
        }
    } catch (_: Exception) {
    }
    extras?.forEach { (key, value) -> map[key] = value }
    return map
}
