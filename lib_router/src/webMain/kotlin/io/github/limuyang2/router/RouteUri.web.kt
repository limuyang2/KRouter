package io.github.limuyang2.router

/** URI parsing shared by JS and Wasm, including custom router schemes and relative paths. */
actual class RouteUri internal constructor(private val value: String) {
    private val match = requireNotNull(URI_PATTERN.matchEntire(value))
    actual val scheme: String? = match.groups[1]?.value
    private val authority = match.groups[2]?.value
    actual val host: String? = authority?.substringAfterLast('@')?.let {
        if (it.startsWith('[')) it.substringBefore(']') + "]" else it.substringBefore(':')
    }
    actual val path: String? = decodeComponent(match.groupValues[3], plusAsSpace = false)
    actual val query: String? = match.groups[4]?.value
    private val fragment = match.groups[5]?.value

    private val queryItems: Map<String, List<String>> by lazy {
        val result = linkedMapOf<String, MutableList<String>>()
        query?.split('&')?.filter { it.isNotEmpty() }?.forEach { pair ->
            val key = decodeComponent(pair.substringBefore('='), plusAsSpace = true)
            val value = decodeComponent(pair.substringAfter('=', ""), plusAsSpace = true)
            result.getOrPut(key) { mutableListOf() }.add(value)
        }
        result
    }

    actual fun getQueryParameter(key: String): String? = queryItems[key]?.firstOrNull()
    actual fun getQueryParameterNames(): Set<String> = queryItems.keys
    actual fun getQueryParameters(key: String): List<String> = queryItems[key].orEmpty()
    actual fun buildUpon(): Builder = Builder(this)
    actual override fun toString(): String = value
    override fun equals(other: Any?): Boolean = other is RouteUri && value == other.value
    override fun hashCode(): Int = value.hashCode()

    actual class Builder internal constructor(uri: RouteUri?) {
        actual constructor() : this(null)

        private var scheme = uri?.scheme
        private var authority = uri?.authority
        private var path = uri?.match?.groupValues?.get(3)
        private val fragment = uri?.fragment
        private val queryItems = uri?.queryItems?.flatMap { (key, values) ->
            values.map { key to it }
        }?.toMutableList() ?: mutableListOf()

        actual fun scheme(scheme: String?): Builder = apply { this.scheme = scheme }
        actual fun authority(authority: String?): Builder = apply { this.authority = authority }
        actual fun path(path: String?): Builder = apply {
            this.path = path?.let {
                encodeComponent(if (it.isEmpty() || it.startsWith('/')) it else "/$it", keepSlash = true)
            }
        }
        actual fun clearQuery(): Builder = apply { queryItems.clear() }
        actual fun appendQueryParameter(key: String, value: String?): Builder = apply {
            queryItems.add(key to (value ?: ""))
        }
        actual fun build(): RouteUri? = buildString {
            scheme?.let { append(it).append(':') }
            authority?.let { append("//").append(it) }
            path?.let { append(it) }
            if (queryItems.isNotEmpty()) {
                append('?')
                append(queryItems.joinToString("&") { (key, value) ->
                    "${encodeComponent(key)}=${encodeComponent(value)}"
                })
            }
            fragment?.let { append('#').append(it) }
        }.toUriOrNull()
    }
}

private val URI_PATTERN = Regex("^(?:([A-Za-z][A-Za-z0-9+.-]*):)?(?://([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#(.*))?$")

internal actual fun String.toUriOrNull(): RouteUri? {
    if (isBlank() || any { it.isWhitespace() || it in "<>\\\"{}|^`" }) return null
    return try {
        // Reject invalid escapes at parse time, including in the query and fragment.
        decodeComponent(this, plusAsSpace = false)
        val uri = RouteUri(this)
        if (uri.scheme == null && ':' in substringBefore('/').substringBefore('?').substringBefore('#')) null else uri
    } catch (error: IllegalArgumentException) {
        logE { "[$this] url 解析失败: $error" }
        null
    }
}

private fun decodeComponent(value: String, plusAsSpace: Boolean): String = buildString {
    var index = 0
    while (index < value.length) {
        when (val character = value[index]) {
            '%' -> {
                val bytes = mutableListOf<Byte>()
                while (index < value.length && value[index] == '%') {
                    require(index + 2 < value.length) { "Incomplete URI escape" }
                    val high = requireNotNull(value[index + 1].digitToIntOrNull(16)) { "Invalid URI escape" }
                    val low = requireNotNull(value[index + 2].digitToIntOrNull(16)) { "Invalid URI escape" }
                    bytes.add(((high shl 4) or low).toByte())
                    index += 3
                }
                append(bytes.toByteArray().decodeToString())
            }
            else -> {
                append(if (character == '+' && plusAsSpace) ' ' else character)
                index++
            }
        }
    }
}

private fun encodeComponent(value: String, keepSlash: Boolean = false): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xff
        val character = code.toChar()
        if (character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' ||
            character in "-._~" || (keepSlash && character == '/')) {
            append(character)
        } else {
            append('%').append("0123456789ABCDEF"[code shr 4]).append("0123456789ABCDEF"[code and 15])
        }
    }
}
