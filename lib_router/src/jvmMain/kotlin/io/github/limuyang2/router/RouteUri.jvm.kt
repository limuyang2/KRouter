package io.github.limuyang2.router

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

actual class RouteUri internal constructor(
    private val uri: URI,
) {
    actual val scheme: String?
        get() = uri.scheme

    actual val host: String?
        get() = uri.host ?: uri.authority

    actual val path: String?
        get() = uri.path

    actual val query: String?
        get() = uri.rawQuery

    private val queryItemsByName: Map<String, List<String>> by lazy {
        val result = linkedMapOf<String, MutableList<String>>()
        val q = uri.rawQuery ?: return@lazy result
        q.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx >= 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                result.getOrPut(key) { mutableListOf() }.add(value)
            } else if (pair.isNotEmpty()) {
                val key = URLDecoder.decode(pair, "UTF-8")
                result.getOrPut(key) { mutableListOf() }.add("")
            }
        }
        result
    }

    actual fun getQueryParameter(key: String): String? = queryItemsByName[key]?.firstOrNull()

    actual fun getQueryParameterNames(): Set<String> = queryItemsByName.keys

    actual fun getQueryParameters(key: String): List<String> = queryItemsByName[key].orEmpty()

    actual fun buildUpon(): Builder = Builder(this)

    actual class Builder internal constructor(
        uri: RouteUri?,
    ) {
        actual constructor() : this(null)

        private var _scheme: String? = uri?.scheme
        private var _authority: String? = uri?.host
        private var _path: String? = uri?.path
        private val _queryItems: MutableList<Pair<String, String?>> = uri?.queryItemsByName?.flatMap { (k, vs) ->
            vs.map { v -> k to v }
        }?.toMutableList() ?: mutableListOf()

        actual fun scheme(scheme: String?): Builder {
            _scheme = scheme
            return this
        }

        actual fun authority(authority: String?): Builder {
            _authority = authority
            return this
        }

        actual fun path(path: String?): Builder {
            _path = when {
                path.isNullOrBlank() -> ""
                path.startsWith("/") -> path
                else -> "/$path"
            }
            return this
        }

        actual fun clearQuery(): Builder {
            _queryItems.clear()
            return this
        }

        actual fun appendQueryParameter(key: String, value: String?): Builder {
            _queryItems.add(key to value)
            return this
        }

        actual fun build(): RouteUri? {
            return try {
                val queryString = _queryItems.joinToString("&") { (k, v) ->
                    "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v ?: "", "UTF-8")}"
                }
                val sb = StringBuilder()
                _scheme?.let { sb.append(it).append(":") }
                _authority?.let { sb.append("//").append(it) }
                _path?.let { sb.append(it) }
                if (queryString.isNotEmpty()) sb.append("?").append(queryString)
                val built = URI(sb.toString())
                RouteUri(built)
            } catch (_: Exception) {
                null
            }
        }
    }

    actual override fun toString(): String = uri.toString()

    override fun equals(other: Any?): Boolean {
        return other is RouteUri && this.toString() == other.toString()
    }

    override fun hashCode(): Int = toString().hashCode()
}

internal actual fun String.toUriOrNull(): RouteUri? {
    if (isBlank()) return null
    return try {
        RouteUri(URI(this))
    } catch (e: Exception) {
        logE { "[${this}] url 解析失败: $e" }
        null
    }
}
