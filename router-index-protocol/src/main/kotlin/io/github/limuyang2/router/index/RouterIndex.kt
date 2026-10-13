package io.github.limuyang2.router.index

import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import java.io.StringReader
import java.security.MessageDigest

/** Shared build-time contract, compiled into both the processor and the Gradle plugin. */
data class RouterIndex(val moduleId: String, val registries: List<String>) {
    init {
        require(moduleId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]*"))) {
            "router.moduleId must be a stable, non-empty identifier, for example com.example:feature-home"
        }
        registries.forEach(::requireQualifiedName)
        require(registries.distinct().size == registries.size) { "Duplicate registry in module $moduleId" }
    }

    /** Does not depend on checkout location, source files, target, or module version. */
    val suffix: String
        get() = MessageDigest.getInstance("SHA-256").digest(moduleId.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }

    /** KSP also packages resources in JVM/Android artifacts; each module must own a distinct path. */
    val resourcePath: String
        get() = "META-INF/io.github.limuyang2.router/$suffix/router-index.json"

    fun encode(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": $SCHEMA_VERSION,")
        appendLine("  \"registryApiVersion\": $REGISTRY_API_VERSION,")
        appendLine("  \"moduleId\": \"$moduleId\",")
        appendLine("  \"registries\": [${registries.sorted().joinToString(", ") { "\"$it\"" }}]")
        appendLine("}")
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val REGISTRY_API_VERSION = 1

        fun decode(text: String): RouterIndex {
            val reader = JsonReader(StringReader(text)).apply { strictness = Strictness.STRICT }
            val root = JsonParser.parseReader(reader)
            require(reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT) { "Trailing index content" }
            require(root.isJsonObject) { "Router index must be an object" }
            val obj = root.asJsonObject
            fun version(name: String, expected: Int) {
                val value = obj.get(name)
                require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber &&
                    value.toString() == expected.toString()) { "Unsupported $name: $value (expected $expected)" }
            }
            version("schemaVersion", SCHEMA_VERSION)
            version("registryApiVersion", REGISTRY_API_VERSION)
            val id = obj.get("moduleId")
            require(id != null && id.isJsonPrimitive && id.asJsonPrimitive.isString) { "Missing moduleId" }
            val names = obj.get("registries")
            require(names != null && names.isJsonArray) { "Missing registries array" }
            return RouterIndex(id.asString, names.asJsonArray.map {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Registry name must be a string" }
                it.asString
            })
        }

        fun requireQualifiedName(name: String) {
            require(name.isNotBlank() && name.split('.').all { part ->
                part.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && part !in keywords && part != "_"
            }) { "Invalid Kotlin qualified name: $name" }
        }

        private val keywords = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
            "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "when", "while",
        )
    }
}
