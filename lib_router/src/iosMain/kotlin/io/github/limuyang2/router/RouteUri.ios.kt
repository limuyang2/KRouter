package io.github.limuyang2.router

import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem

actual class RouteUri internal constructor(
    private val url: NSURL,
) {
    private val components: NSURLComponents = NSURLComponents(string = url.absoluteString ?: "")

    actual val scheme: String?
        get() = components.scheme

    actual val host: String?
        get() = components.host

    actual val path: String?
        get() = components.path

    actual val query: String?
        get() = components.query

    private val queryItemsByName: Map<String, List<String>> by lazy {
        val result = linkedMapOf<String, MutableList<String>>()
        val items = components.queryItems ?: emptyList<Any?>()
        items.forEach { item ->
            val queryItem = item as? NSURLQueryItem ?: return@forEach
            val name = queryItem.name
            val value = queryItem.value ?: ""
            result.getOrPut(name) { mutableListOf() }.add(value)
        }
        result
    }

    actual fun getQueryParameter(key: String): String? = queryItemsByName[key]?.firstOrNull()

    actual fun getQueryParameterNames(): Set<String> = queryItemsByName.keys

    actual fun getQueryParameters(key: String): List<String> = queryItemsByName[key].orEmpty()

    actual fun buildUpon(): RouteUri.Builder = RouteUri.Builder(this)

    actual class Builder internal constructor(
        uri: RouteUri?,
    ) {
        actual constructor() : this(null)

        private val components = NSURLComponents().apply {
            scheme = uri?.scheme
            host = uri?.host
            path = uri?.path
            queryItems = uri?.toQueryItems()
        }

        actual fun scheme(scheme: String?): Builder {
            components.scheme = scheme
            return this
        }

        actual fun authority(authority: String?): Builder {
            components.host = authority
            return this
        }

        actual fun path(path: String?): Builder {
            components.path = when {
                path.isNullOrBlank() -> ""
                path.startsWith("/") -> path
                else -> "/$path"
            }
            return this
        }

        actual fun clearQuery(): Builder {
            components.queryItems = null
            components.query = null
            return this
        }

        actual fun appendQueryParameter(key: String, value: String?): Builder {
            val current = (components.queryItems ?: emptyList<Any?>()).toMutableList()
            current += NSURLQueryItem(name = key, value = value)
            components.queryItems = current
            return this
        }

        actual fun build(): RouteUri? {
            val builtUrl = components.URL ?: return null
            return RouteUri(builtUrl)
        }
    }

    actual override fun toString(): String = url.absoluteString ?: ""

    override fun equals(other: Any?): Boolean {
        return other is RouteUri && this.toString() == other.toString()
    }

    override fun hashCode(): Int = toString().hashCode()

    private fun toQueryItems(): List<NSURLQueryItem> {
        return queryItemsByName.flatMap { (key, values) ->
            values.map { value -> NSURLQueryItem(name = key, value = value) }
        }
    }
}

internal actual fun String.toUriOrNull(): RouteUri? {
    if (isBlank()) return null
    return try {
        RouteUri(NSURL(string = this))
    } catch (e: Exception) {
        logE { "[${this}] url 解析失败: $e" }
        null
    }
}
