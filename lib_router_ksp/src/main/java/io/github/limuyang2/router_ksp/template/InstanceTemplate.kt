package io.github.limuyang2.router_ksp.template

import com.google.devtools.ksp.symbol.ClassKind

internal fun createRouterInstanceTemplate(
    packageName: String,
    className: String,
    classKind: ClassKind,
    targetQualifiedName: String,
    constructorParamsInfos: List<ConstructorParamInfo>, // 构造函数的每一个参数
    mutableProperties: List<VarInjectedProperty>, //被注解的 var 成员属性 (不在构造函数中的)
): String {
    val imports = buildString {
        if (needsToImmutableListImport(constructorParamsInfos, mutableProperties)) {
            appendLine("import kotlinx.collections.immutable.toImmutableList")
        }
        if (needsToImmutableSetImport(constructorParamsInfos, mutableProperties)) {
            appendLine("import kotlinx.collections.immutable.toImmutableSet")
        }
        append("import io.github.limuyang2.router.IRouterInstance")
    }

    return """
package $packageName

$imports

/**
 * 路由目标类的实例生成器，由 KSP 自动生成
 */
internal class $className : IRouterInstance {

    override fun instance(extras: Map<String, Any>?): $targetQualifiedName {
        ${
        when (classKind) {
            ClassKind.OBJECT -> {
                "return $targetQualifiedName"
            }
            ClassKind.CLASS -> {
                getInstanceTemplate(targetQualifiedName, constructorParamsInfos, mutableProperties)
            }
            else -> {
                throw IllegalArgumentException("[$targetQualifiedName] must be a Class or Object")
            }
        }
    }
    }
}
""".trim()
}

/**
 * 检查注入的构造参数和 var 属性中是否包含 ImmutableList / ImmutableCollection 类型，
 * 以决定是否需要生成 toImmutableList 的 import。
 * 仅检查 injectedProperty != null 的构造参数，因为未注入的参数走 typeDefaultValue（使用全限定名）。
 */
private fun needsToImmutableListImport(
    constructorParamsInfos: List<ConstructorParamInfo>,
    mutableProperties: List<VarInjectedProperty>,
): Boolean {
    val types = setOf(
        "kotlinx.collections.immutable.ImmutableList",
        "kotlinx.collections.immutable.ImmutableCollection",
    )
    return constructorParamsInfos.any { it.injectedProperty != null && it.typeName in types } ||
            mutableProperties.any { it.typeName in types }
}

/**
 * 检查是否需要生成 toImmutableSet 的 import，逻辑同上。
 */
private fun needsToImmutableSetImport(
    constructorParamsInfos: List<ConstructorParamInfo>,
    mutableProperties: List<VarInjectedProperty>,
): Boolean {
    return constructorParamsInfos.any {
        it.injectedProperty != null && it.typeName == "kotlinx.collections.immutable.ImmutableSet"
    } || mutableProperties.any { it.typeName == "kotlinx.collections.immutable.ImmutableSet" }
}

// ------ 内容有些复杂，分开来写 ------


private fun getInstanceTemplate(
    targetQualifiedName: String,
    constructorParamsInfos: List<ConstructorParamInfo>, // 构造函数的每一个参数
    mutableProperties: List<VarInjectedProperty>, //被注解的 var 成员属性 (不在构造函数中的)
) = """
    ${
        if (constructorParamsInfos.isEmpty() && mutableProperties.isEmpty()) {
            ""
        } else {
            "        val finalExtras = extras ?: emptyMap()\n"
        }
    }
${getParamDeclarations(constructorParamsInfos)}
        // 创建实例
    ${
        if (constructorParamsInfos.isNotEmpty()) {
            "    val instance = $targetQualifiedName(\n" +
            "            ${getConstructorParams(constructorParamsInfos)}\n" +
            "        )"
        } else {
            "    val instance = $targetQualifiedName()"
        }
    }

        ${otherProperty(mutableProperties)}

        return instance
""".trim()

/**
 * 生成参数变量的声明文本
 *
 * 例：
 *     val a = ...
 *     val b = ...
 */
private fun getParamDeclarations(constructorParamsInfos: List<ConstructorParamInfo>): String {
    if (constructorParamsInfos.isEmpty()) return ""


    val declarations = constructorParamsInfos.mapNotNull { param ->
        val paramName = param.name
        if (param.injectedProperty != null) {
            "        val $paramName = ${
                param.typeName.mapGetWithDefault(
                    param.injectedProperty.paramKey,
                    param.isNullable,
                    param.genericsTypeNames
                )
            }"
        } else {
            if (param.hasDefault) {
                null
            } else {
                val defaultValue = if (param.isNullable) "null" else typeDefaultValue(param.typeName)
                "        val $paramName = $defaultValue"
            }
        }
    }
    return if (declarations.isEmpty()) "" else declarations.joinToString("\n") + "\n"
}

/**
 * 生成构造方法传参的文本（仅引用变量名）
 *
 * 例：
 *     AClass(
 *       a = a,
 *       b = b,
 *     )
 */
private fun getConstructorParams(constructorParamsInfos: List<ConstructorParamInfo>): String {
    return constructorParamsInfos
        .filter { it.injectedProperty != null || !it.hasDefault }
        .joinToString(",\n            ") { "${it.name} = ${it.name}" }
}


/**
 * 为可变的 var 成员变量赋值（加了注解的var）
 */
private fun otherProperty(mutableProperties: List<VarInjectedProperty>): String {
    if (mutableProperties.isEmpty()) return ""

    return mutableProperties.joinToString("\n        ") { prop ->
        "instance.${prop.originalName} = ${
            prop.typeName.mapGetWithDefault(
                prop.paramKey,
                prop.isNullable,
                prop.genericsTypeNames
            )
        }"
    }
}

/**
 * 给注解的参数赋值。
 * 优先从 finalExtras 取目标类型值，取不到再尝试从 List<String> 转换。
 */
private fun String.mapGetWithDefault(key: String, isNullable: Boolean, genericsTypeNames: List<String>): String{

    // 构造完整的泛型类型签名，用于 as? 转换
    val fullTypeName = if (genericsTypeNames.isNotEmpty()) {
        "$this<${genericsTypeNames.joinToString(", ")}>"
    } else {
        this
    }

    // 对象通道：finalExtras["key"] as? Type
    val objAccess = "(finalExtras[\"$key\"] as? $fullTypeName)"

    // 字符串转换逻辑（从 extras 中取 List<String> 后转换）
    val strConversion = "((finalExtras[\"$key\"] as? List<String>)?.${
        when (this) {
            "kotlin.String" -> "getOrNull(0)"
            "kotlin.Int" -> "getOrNull(0)?.toIntOrNull()"
            "kotlin.Long" -> "getOrNull(0)?.toLongOrNull()"
            "kotlin.Float" -> "getOrNull(0)?.toFloatOrNull()"
            "kotlin.Double" -> "getOrNull(0)?.toDoubleOrNull()"
            "kotlin.Boolean" -> "getOrNull(0)?.toBoolean()"
            "kotlin.Short" -> "getOrNull(0)?.toShortOrNull()"
            "kotlin.Byte" -> "getOrNull(0)?.toByteOrNull()"
            "kotlin.collections.List",
            "kotlinx.collections.immutable.ImmutableList",
            "kotlinx.collections.immutable.ImmutableCollection" -> {
                if (genericsTypeNames.getOrNull(0) == "kotlin.String") {
                    // String 元素不需要逐个转换
                    when (this) {
                        "kotlin.collections.List" -> {
                            // as? List<String> 已能匹配 URL 参数，跳过 strConversion
                            return if (isNullable) objAccess else "$objAccess ?: ${typeDefaultValue(this)}"
                        }
                        else -> "toImmutableList()"
                    }
                } else {
                    val s = when(genericsTypeNames[0]) {
                        "kotlin.Int" -> "mapNotNull { it.toIntOrNull() }"
                        "kotlin.Long" -> "mapNotNull { it.toLongOrNull() }"
                        "kotlin.Float" -> "mapNotNull { it.toFloatOrNull() }"
                        "kotlin.Double" -> "mapNotNull { it.toDoubleOrNull() }"
                        "kotlin.Boolean" -> "mapNotNull { it.toBoolean() }"
                        "kotlin.Short" -> "mapNotNull { it.toShortOrNull() }"
                        "kotlin.Byte" -> "mapNotNull { it.toByteOrNull() }"
                        else -> return if (isNullable) objAccess else "$objAccess ?: ${typeDefaultValue(this)}"
                    }

                    if (this == "kotlinx.collections.immutable.ImmutableList" ||
                        this == "kotlinx.collections.immutable.ImmutableCollection") {
                        "$s?.toImmutableList()"
                    } else {
                        s
                    }
                }
            }
            "kotlinx.collections.immutable.ImmutableSet" -> {
                if (genericsTypeNames.getOrNull(0) == "kotlin.String") {
                    "toImmutableSet()"
                } else {
                    val s = when (genericsTypeNames.getOrNull(0)) {
                        "kotlin.Int" -> "mapNotNull { it.toIntOrNull() }"
                        "kotlin.Long" -> "mapNotNull { it.toLongOrNull() }"
                        "kotlin.Float" -> "mapNotNull { it.toFloatOrNull() }"
                        "kotlin.Double" -> "mapNotNull { it.toDoubleOrNull() }"
                        "kotlin.Boolean" -> "mapNotNull { it.toBoolean() }"
                        "kotlin.Short" -> "mapNotNull { it.toShortOrNull() }"
                        "kotlin.Byte" -> "mapNotNull { it.toByteOrNull() }"
                        else -> return if (isNullable) objAccess else "$objAccess ?: ${typeDefaultValue(this)}"
                    }
                    "$s?.toImmutableSet()"
                }
            }
            "android.net.Uri" -> {
                "getOrNull(0)?.let{ android.net.Uri.parse(it) }"
            }
            "java.io.File" -> {
                "getOrNull(0)?.let{ java.io.File(it) }"
            }

            else -> {
                // 未知类型无法从字符串转换，仅从对象通道取值
                return if (isNullable) {
                    objAccess
                } else {
                    "$objAccess ?: ${typeDefaultValue(this)}"
                }
            }
        }
    })"

    // 拼接：优先对象通道，fallback 字符串转换
    val result = "$objAccess ?: $strConversion"

    if (!isNullable) {
        return "$result ?: ${typeDefaultValue(this)}"
    }

    return result
}

/**
 * 用于获取类型的默认值
 */
private fun typeDefaultValue(type: String) = when (type) {
    "kotlin.String" -> "\"\""
    "kotlin.Int" -> "0"
    "kotlin.Boolean" -> "false"
    "kotlin.Float" -> "0f"
    "kotlin.Long" -> "0L"
    "kotlin.Double" -> "0.0"
    "kotlin.Short" -> "0"
    "kotlin.Byte" -> "0"
    "kotlin.UInt" -> "0u"
    "kotlin.UByte" -> "0u"
    "kotlin.UShort" -> "0u"
    "kotlin.ULong" -> "0uL"
    "kotlin.Char" -> "'\\u0000'"
    "kotlin.Array" -> "emptyArray()"
    "kotlin.IntArray" -> "intArrayOf()"
    "kotlin.LongArray" -> "longArrayOf()"
    "kotlin.FloatArray" -> "floatArrayOf()"
    "kotlin.DoubleArray" -> "doubleArrayOf()"
    "kotlin.BooleanArray" -> "booleanArrayOf()"
    "kotlin.ShortArray" -> "shortArrayOf()"
    "kotlin.ByteArray" -> "byteArrayOf()"
    "kotlin.CharArray" -> "charArrayOf()"
    "kotlin.UIntArray" -> "uintArrayOf()"
    "kotlin.ULongArray" -> "ulongArrayOf()"
    "kotlin.UByteArray" -> "ubyteArrayOf()"
    "kotlin.UShortArray" -> "ushortArrayOf()"

    "kotlinx.collections.immutable.ImmutableSet" -> "kotlinx.collections.immutable.persistentSetOf()"
    "kotlinx.collections.immutable.ImmutableMap" -> "kotlinx.collections.immutable.persistentMapOf()"
    "kotlinx.collections.immutable.ImmutableList",
    "kotlinx.collections.immutable.ImmutableCollection" -> "kotlinx.collections.immutable.persistentListOf()"

    "kotlin.collections.List" -> "emptyList()"
    "kotlin.collections.ArrayList" -> "ArrayList()"
    "kotlin.collections.Map" -> "emptyMap()"
    "kotlin.collections.HashMap" -> "HashMap()"
    "kotlin.collections.Set" -> "emptySet()"
    "kotlin.collections.HashSet" -> "HashSet()"

    "android.net.Uri" -> "android.net.Uri.parse(\"\")"
    "java.io.File" -> "java.io.File(\"\")"
    else -> "${type}()"
}


// var 属性信息
internal data class VarInjectedProperty(
    val paramKey: String,       // URL中的参数名
    val originalName: String,   // 属性自身的名称
    val typeName: String,       // 属性的完全限定类型名
    val genericsTypeNames: List<String>, // 范型类型字符串
    val isMutable: Boolean,      // 是 val 还是 var
    val isNullable: Boolean,
)



// 构造函数里注解的参数信息
internal data class ConstructorInjectedProperty(
    val paramKey: String,       // URL中的参数名
)

// 管理构造函数参数的信息
internal data class ConstructorParamInfo(
    val name: String,
    val typeName: String,
    val genericsTypeNames: List<String>, // 范型类型字符串
    val isNullable: Boolean,
    val hasDefault: Boolean,
    val injectedProperty: ConstructorInjectedProperty?, // 如果被 @RouterParam 注解，则不为null
)