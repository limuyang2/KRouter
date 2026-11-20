package io.github.limuyang2.router

/**
 * 封装全局 Push 拦截器实例及其优先级信息。
 *
 * 由模块路由表 [ModuleRouter.globalPushInterceptors] 提供，用于在初始化时排序并组装拦截器链。
 *
 * Encapsulates a global push interceptor instance along with its priority.
 *
 * Provided by [ModuleRouter.globalPushInterceptors] to sort and assemble the interceptor chain during initialization.
 *
 * @param interceptor 全局 Push 拦截器实例 / Global push interceptor instance.
 * @param priority 优先级数值，数值越小越先执行 / Priority value; smaller values run first.
 * @param navigatorTags 适用的 Navigator 标签；为空时适用于所有 Navigator / Navigator tags this interceptor applies to; empty means all Navigators.
 */
class GlobalPushInterceptorInfo(
    val interceptor: IRouterPushInterceptor,
    val priority: Int,
    val navigatorTags: Set<String> = emptySet(),
)

/**
 * 封装全局 Pop 拦截器实例及其优先级信息。
 *
 * 由模块路由表 [ModuleRouter.globalPopInterceptors] 提供，用于在初始化时排序并组装拦截器链。
 *
 * Encapsulates a global pop interceptor instance along with its priority.
 *
 * Provided by [ModuleRouter.globalPopInterceptors] to sort and assemble the interceptor chain during initialization.
 *
 * @param interceptor 全局 Pop 拦截器实例 / Global pop interceptor instance.
 * @param priority 优先级数值，数值越小越先执行 / Priority value; smaller values run first.
 * @param navigatorTags 适用的 Navigator 标签；为空时适用于所有 Navigator / Navigator tags this interceptor applies to; empty means all Navigators.
 */
class GlobalPopInterceptorInfo(
    val interceptor: IRouterPopInterceptor,
    val priority: Int,
    val navigatorTags: Set<String> = emptySet(),
)
