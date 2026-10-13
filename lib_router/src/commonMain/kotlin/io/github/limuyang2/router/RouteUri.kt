package io.github.limuyang2.router

/**
 * 跨平台 URI 抽象，提供 scheme / host / path / query 的解析能力。
 * 各平台通过 expect/actual 对接 Uri (Android) / NSURLComponents (iOS) / java.net.URI (JVM)。
 */
expect class RouteUri {

    /** URI 的协议部分，如 "https"、"test"。 */
    val scheme: String?

    /** URI 的主机部分，如 "example.com"。 */
    val host: String?

    /** URI 的路径部分，如 "/a/b"。 */
    val path: String?

    /** URI 的查询字符串部分，不包含 '?'。 */
    val query: String?

    /**
     * 获取指定 key 的第一个 query 参数值。
     */
    fun getQueryParameter(key: String): String?

    /**
     * 获取所有 query 参数的 key 集合。
     */
    fun getQueryParameterNames(): Set<String>

    /**
     * 获取指定 key 的所有 query 参数值列表。
     */
    fun getQueryParameters(key: String): List<String>

    /**
     * 基于当前 URI 创建一个新的 Builder。
     */
    fun buildUpon(): Builder

    override fun toString(): String

    /**
     * URI 构建器，用于链式构造 RouteUri 实例。
     */
    class Builder {

        /**
         * 创建一个空的 Builder。
         */
        constructor()

        /**
         * 设置 URI 的协议部分。
         */
        fun scheme(scheme: String?): Builder

        /**
         * 设置 URI 的授权部分（host:port）。
         */
        fun authority(authority: String?): Builder

        /**
         * 设置 URI 的路径部分。
         */
        fun path(path: String?): Builder

        /**
         * 清除所有已添加的 query 参数。
         */
        fun clearQuery(): Builder

        /**
         * 追加一个 query 参数键值对。
         */
        fun appendQueryParameter(key: String, value: String?): Builder

        /**
         * 构建并返回 RouteUri，构建失败时返回 null。
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
