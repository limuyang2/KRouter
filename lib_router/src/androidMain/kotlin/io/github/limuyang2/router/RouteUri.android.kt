package io.github.limuyang2.router

import android.net.Uri

actual class RouteUri internal constructor(
    internal val delegate: Uri,
) {
    actual val scheme: String? get() = delegate.scheme
    actual val host: String? get() = delegate.host
    actual val path: String? get() = delegate.path
    actual val query: String? get() = delegate.query

    actual fun getQueryParameter(key: String): String? = delegate.getQueryParameter(key)

    actual fun getQueryParameterNames(): Set<String> = delegate.queryParameterNames

    actual fun getQueryParameters(key: String): List<String> = delegate.getQueryParameters(key)

    actual fun buildUpon(): RouteUri.Builder = RouteUri.Builder(delegate.buildUpon())

    actual class Builder internal constructor(
        private val builder: Uri.Builder,
    ) {
        actual constructor() : this(Uri.Builder())

        actual fun scheme(scheme: String?): Builder {
            builder.scheme(scheme)
            return this
        }

        actual fun authority(authority: String?): Builder {
            builder.authority(authority)
            return this
        }

        actual fun path(path: String?): Builder {
            builder.path(
                when {
                    path.isNullOrBlank() -> null
                    path.startsWith("/") -> path
                    else -> "/$path"
                }
            )
            return this
        }

        actual fun clearQuery(): Builder {
            builder.clearQuery()
            return this
        }

        actual fun appendQueryParameter(key: String, value: String?): Builder {
            builder.appendQueryParameter(key, value)
            return this
        }

        actual fun build(): RouteUri? {
            return try {
                RouteUri(builder.build())
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is RouteUri && delegate == other.delegate
    }

    override fun hashCode(): Int = delegate.hashCode()

    actual override fun toString(): String = delegate.toString()
}

internal actual fun String.toUriOrNull(): RouteUri? {
    if (isBlank()) return null
    return try {
        RouteUri(Uri.parse(this))
    } catch (e: Exception) {
        logE { "[${this}] url 解析失败: $e" }
        null
    }
}
