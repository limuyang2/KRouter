package io.github.limuyang2.router_ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier

/**
 * 拦截器类的代码生成契约检查结果。
 *
 * [implementsInterface] 为 false 时已记录编译错误，调用方应跳过该类；
 * [noArgConstruction] 为 false 时生成代码不含无参构造兜底，实例必须由运行时
 * `interceptorResolver` 提供（DI-only 类）。
 */
internal class InterceptorContract(
    val implementsInterface: Boolean,
    val noArgConstruction: Boolean,
)

/**
 * 校验拦截器类契约：必须实现对应拦截器接口；无可从生成代码调用的无参构造时提示需要 interceptorResolver。
 *
 * @param annotationLabel 注解显示名，用于错误提示 / Annotation display name for diagnostics.
 * @param interfaceQualifiedName 要求实现的接口全限定名 / Required interface FQN.
 */
internal fun KSClassDeclaration.resolveInterceptorContract(
    logger: KSPLogger,
    annotationLabel: String,
    interfaceQualifiedName: String,
): InterceptorContract {
    val elementQualifiedName = qualifiedName?.asString() ?: "<anonymous>"
    if (!implementsType(interfaceQualifiedName)) {
        logger.error(
            "RouteProcessor: $annotationLabel target [$elementQualifiedName] must implement ${interfaceQualifiedName.substringAfterLast('.')}.",
            this
        )
        return InterceptorContract(implementsInterface = false, noArgConstruction = false)
    }
    val noArg = hasNoArgConstruction()
    if (!noArg) {
        logger.warn(
            "RouteProcessor: $annotationLabel target [$elementQualifiedName] 没有可从生成代码调用的无参构造" +
                "（不存在、参数无默认值或 private/protected），实例将由 initRouter 配置的" +
                "interceptorResolver 提供（如 Koin）；未配置或未命中会在 Navigator 构造时报错。",
            this
        )
    }
    return InterceptorContract(implementsInterface = true, noArgConstruction = noArg)
}

/**
 * 类是否可用无参构造调用：主构造全部参数有默认值（含无参数），
 * 或任一次级构造满足同样条件（如 `constructor() : this(Repo())`）。
 * 构造器以「与类同名的函数声明」识别，兼容无显式构造（合成主构造不在 declarations 中）。
 * private/protected 构造对生成代码（同模块不同包的 Provider）不可调用，不视为兜底，
 * 此类输入归入 resolver-only 形态。
 * object 由调用方按 classKind 分支，不经过本函数。
 */
internal fun KSClassDeclaration.hasNoArgConstruction(): Boolean {
    val primary = primaryConstructor
    if (primary != null && primary.isCallableFromGeneratedCode() &&
        primary.parameters.all { it.hasDefault }
    ) return true
    val className = simpleName.asString()
    return declarations.asSequence()
        .filterIsInstance<KSFunctionDeclaration>()
        .filter { it.simpleName.asString() == className && it != primary }
        .any { it.isCallableFromGeneratedCode() && it.parameters.all { param -> param.hasDefault } }
}

/** 生成代码与拦截器同模块不同包：public/internal 可调用，private/protected 不可。 */
private fun KSFunctionDeclaration.isCallableFromGeneratedCode(): Boolean {
    return Modifier.PRIVATE !in modifiers && Modifier.PROTECTED !in modifiers
}

/** 递归检查（含超类）是否实现了指定全限定名接口。 */
internal fun KSClassDeclaration.implementsType(qualifiedName: String): Boolean {
    return superTypes.any { typeRef ->
        val declaration = typeRef.resolve().declaration
        declaration.qualifiedName?.asString() == qualifiedName ||
                (declaration as? KSClassDeclaration)?.implementsType(qualifiedName) == true
    }
}
