package io.github.limuyang2.router

import kotlin.reflect.KClass

/**
 * 解析全局或页面级 Push 拦截器实例，带无参构造兜底；主要由 KSP 生成代码调用，
 * 手写 [ModuleRouter] 需要同样的 DI 行为时也可使用。
 *
 * 先询问 [RouterSingleton.Config.interceptorResolver]（如 Koin）：返回实例则校验并采用；
 * 返回 null 或未注册则调用 [fallback] 的无参构造。object 拦截器不经过本函数。
 *
 * Resolves a global or page-level push interceptor with a no-arg construction fallback;
 * primarily invoked by KSP-generated code, and usable by hand-written [ModuleRouter]s
 * that need the same DI behavior.
 *
 * Consults [RouterSingleton.Config.interceptorResolver] (e.g. Koin) first: a returned
 * instance is validated and adopted; null or an absent resolver falls back to [fallback].
 * Object interceptors never pass through this function.
 *
 * @param clazz 注解声明的拦截器类 / The annotated interceptor class.
 * @param fallback 生成代码的无参构造兜底 / Generated no-arg construction fallback.
 * @return 拦截器实例 / The interceptor instance.
 * @throws IllegalStateException 解析器返回的类型未实现 [IRouterPushInterceptor] / The resolver returned a type not implementing [IRouterPushInterceptor].
 */
fun resolvePushInterceptor(
    clazz: KClass<*>,
    fallback: () -> IRouterPushInterceptor,
): IRouterPushInterceptor {
    resolveInstanceOrNull(clazz)?.let { resolved ->
        check(resolved is IRouterPushInterceptor) {
            "interceptorResolver 返回的 ${resolved::class} 未实现 IRouterPushInterceptor，" +
                "无法作为 ${clazz} 的拦截器实例"
        }
        logI { "Push 拦截器 ${clazz} 由 interceptorResolver 提供：${resolved::class}" }
        return resolved
    }
    val instance = fallback()
    logI { "Push 拦截器 ${clazz} 由无参构造兜底提供" }
    return instance
}

/**
 * 解析仅能由 [RouterSingleton.Config.interceptorResolver] 提供的 Push 拦截器实例（类没有可用的
 * 无参构造，生成代码不含兜底）。主要由 KSP 生成代码调用。
 *
 * Resolves a push interceptor that only [RouterSingleton.Config.interceptorResolver] can supply
 * (the class has no usable no-arg construction, so generated code carries no fallback);
 * primarily invoked by KSP-generated code.
 *
 * @param clazz 注解声明的拦截器类 / The annotated interceptor class.
 * @return 拦截器实例 / The interceptor instance.
 * @throws IllegalStateException 解析器未注册、返回 null 或类型不符 / The resolver is absent, misses, or returns a mismatched type.
 */
fun resolvePushInterceptor(clazz: KClass<*>): IRouterPushInterceptor =
    resolvePushInterceptor(clazz) { interceptorResolverRequired("Push", clazz) }

/**
 * 解析全局或页面级 Pop 拦截器实例，带无参构造兜底；语义与 [resolvePushInterceptor] 对称。
 *
 * Resolves a global or page-level pop interceptor with a no-arg construction fallback;
 * symmetric to [resolvePushInterceptor].
 *
 * @param clazz 注解声明的拦截器类 / The annotated interceptor class.
 * @param fallback 生成代码的无参构造兜底 / Generated no-arg construction fallback.
 * @return 拦截器实例 / The interceptor instance.
 * @throws IllegalStateException 解析器返回的类型未实现 [IRouterPopInterceptor] / The resolver returned a type not implementing [IRouterPopInterceptor].
 */
fun resolvePopInterceptor(
    clazz: KClass<*>,
    fallback: () -> IRouterPopInterceptor,
): IRouterPopInterceptor {
    resolveInstanceOrNull(clazz)?.let { resolved ->
        check(resolved is IRouterPopInterceptor) {
            "interceptorResolver 返回的 ${resolved::class} 未实现 IRouterPopInterceptor，" +
                "无法作为 ${clazz} 的拦截器实例"
        }
        logI { "Pop 拦截器 ${clazz} 由 interceptorResolver 提供：${resolved::class}" }
        return resolved
    }
    val instance = fallback()
    logI { "Pop 拦截器 ${clazz} 由无参构造兜底提供" }
    return instance
}

/**
 * 解析仅能由 [RouterSingleton.Config.interceptorResolver] 提供的 Pop 拦截器实例（类没有可用的
 * 无参构造，生成代码不含兜底）；语义与 [resolvePushInterceptor] 的同名重载对称。
 *
 * Resolves a pop interceptor that only [RouterSingleton.Config.interceptorResolver] can supply;
 * symmetric to the resolver-only [resolvePushInterceptor] overload.
 *
 * @param clazz 注解声明的拦截器类 / The annotated interceptor class.
 * @return 拦截器实例 / The interceptor instance.
 * @throws IllegalStateException 解析器未注册、返回 null 或类型不符 / The resolver is absent, misses, or returns a mismatched type.
 */
fun resolvePopInterceptor(clazz: KClass<*>): IRouterPopInterceptor =
    resolvePopInterceptor(clazz) { interceptorResolverRequired("Pop", clazz) }

private fun resolveInstanceOrNull(clazz: KClass<*>): Any? {
    return RouterSingleton.getConfigOrNull()?.interceptorResolver?.invoke(clazz)
}

private fun interceptorResolverRequired(direction: String, clazz: KClass<*>): Nothing {
    throw IllegalStateException(
        "$direction 拦截器 ${clazz} 没有可用的无参构造，必须由 interceptorResolver 提供实例；" +
            "请在 initRouter { interceptorResolver { ... } } 中注册解析器（如 Koin）并确保它能解析该类"
    )
}
