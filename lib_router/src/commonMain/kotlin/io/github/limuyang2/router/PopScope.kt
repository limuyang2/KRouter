package io.github.limuyang2.router

import kotlin.reflect.KClass

/**
 * Pop 和拦截器重定向共用的页面选择 DSL；仅配置意图，在拦截前绑定具体实例。
 * to() 保留目标页；last()/all() 只移除匹配项，不携带业务结果。
 * to() 未匹配或目标已是栈顶时不执行；移除操作会清空栈时保留操作前的根页。
 *
 * Selection DSL shared by pop and interceptor redirection; configures intent and binds instances
 * before interception. to() retains the destination; last()/all() remove matches without a business result.
 * Back-to selection does nothing when unmatched or already at the top. Removal retains the original
 * root if every stack entry would otherwise be removed.
 */
open class PopScope internal constructor() {
    internal var popType: PopType = PopType.Current()

    /**
     * 绑定拦截前的栈顶，放行后关闭该实例并返回 NoResult。
     *
     * Binds the top instance before interception; closes it with NoResult when allowed. Retains the root.
     */
    fun top() {
        popType = PopType.Current()
    }

    /**
     * 绑定拦截前的栈顶并明确回传结果，哪怕 result 为 null，也会按 Success(null) 处理。
     *
     * Binds the top instance and explicitly returns a result, including Success(null); retains the root.
     *
     * @param result 业务返回值 / Business result, including explicit null.
     */
    fun top(result: Any?) {
        popType = PopType.Current(PopResult.Provided(result))
    }

    /**
     * 保留 URL 匹配类型的最近页面，移除解析时位于它上方的页面；不投递定向业务值。
     *
     * Retains the nearest page of the URL's route class and removes pages bound above it, without a direct value.
     *
     * @param url 用于查找目标类型的 URL，查询参数不参与匹配 / URL resolving the target class; query parameters are ignored.
     */
    fun to(url: String) {
        backTo(PopType.BackTo.PopDestination.ByUrl(url))
    }

    /**
     * 回退到 URL 匹配类型的最近页面，并按 key 和结果类型直接投递给它的 Consumer。
     *
     * Returns to the nearest page of the URL's route class and delivers directly to its consumer by key and type.
     *
     * @param url 用于查找目标类型的 URL / URL resolving the target route class.
     * @param result 业务值，null 也表示 Success / Business value; null also represents Success.
     * @param key 目标页 [rememberRouteResult] 使用的 key / Key used by the destination's [rememberRouteResult].
     */
    inline fun <reified R : Any> to(url: String, result: R?, key: String) {
        backToWithResult(url, result, key, R::class)
    }

    /**
     * 保留最近的指定类型页面并移除其上方已绑定页面，不投递定向业务值。
     *
     * Retains the nearest page of the given class and removes the bound pages above it, without a direct value.
     *
     * @param clazz 目标路由类型 / Destination route class.
     */
    fun to(clazz: KClass<*>) {
        backTo(PopType.BackTo.PopDestination.ByClass(clazz))
    }

    /**
     * 回退到最近的 T 类型页面，不携带定向业务值
     *
     * Returns to the nearest T page without a direct value.
     */
    inline fun <reified T : Any> to() = to(T::class)

    /**
     * 回退到最近的指定类型页面，并直接投递给其 key 和 R 类型匹配的 Consumer。
     *
     * Returns to the nearest page of the given class and delivers to its consumer matching key and result type R.
     *
     * @param clazz 目标路由类型 / Destination route class.
     * @param result 业务值，null 也表示 Success / Business value; null also represents Success.
     * @param key 目标 Consumer 的 key / Destination consumer key.
     */
    inline fun <reified R : Any> to(clazz: KClass<*>, result: R?, key: String) {
        backToWithResult(clazz, result, key, R::class)
    }

    /**
     * 回退到最近的 D 类型页面，并直接投递给其 key 和 R 类型匹配的 Consumer。
     *
     * Returns to the nearest D page and delivers to its consumer matching key and result type R.
     *
     * @param result 业务值，null 也表示 Success / Business value; null also represents Success.
     * @param key 目标 Consumer 的 key / Destination consumer key.
     */
    inline fun <reified D : Any, reified R : Any> to(result: R?, key: String) {
        backToWithResult(D::class, result, key, R::class)
    }

    /**
     * 回退到指定对象实例，按 === 匹配并保留该对象；不投递定向业务值。
     *
     * Returns to and retains the exact object instance, matching by ===, without a direct value.
     *
     * @param instance 栈中已有的目标实例 / Existing destination instance in the stack.
     */
    fun to(instance: IRouterEntryProvider) {
        backTo(PopType.BackTo.PopDestination.ByInstance(instance))
    }

    /**
     * 回退到按 === 匹配的指定实例，并直接投递给其 key 和 R 类型匹配的 Consumer。
     *
     * Returns to the exact instance matched by === and delivers to its consumer matching key and result type R.
     *
     * @param instance 栈中已有的目标实例 / Existing destination instance in the stack.
     * @param result 业务值，null 也表示 Success / Business value; null also represents Success.
     * @param key 目标 Consumer 的 key / Destination consumer key.
     */
    inline fun <reified R : Any> to(instance: IRouterEntryProvider, result: R?, key: String) {
        backToWithResult(instance, result, key, R::class)
    }

    /**
     * 移除 URL 匹配类型的最近一页，不携带业务结果；与 last(url) 相同。
     *
     * Removes the nearest page of the URL's route class without a business result; equivalent to last(url).
     */
    fun remove(url: String) = remove(PopType.Remove.PopFilter.ByUrl(url), isRemoveAll = false)

    /**
     * 移除最近的指定类型页面，不携带业务结果
     *
     * Removes the nearest page of the given class without a business result.
     */
    fun last(type: KClass<*>) =
        remove(PopType.Remove.PopFilter.ByClasses(setOf(type)), isRemoveAll = false)

    /**
     * 移除最近的 T 类型页面，不携带业务结果
     *
     * Removes the nearest T page without a business result.
     */
    inline fun <reified T : Any> last() = last(T::class)

    /**
     * 从所有候选类型中仅移除离栈顶最近的一页，不是每种类型各移除一页。
     *
     * Removes only the nearest page matching any candidate class, not one page per class.
     */
    fun last(vararg types: KClass<*>) =
        remove(PopType.Remove.PopFilter.ByClasses(types.toSet()), isRemoveAll = false)

    /**
     * 仅移除类型集合中匹配的最近一页
     *
     * Removes only the nearest page whose class belongs to the set.
     */
    fun last(types: Set<KClass<*>>) =
        remove(PopType.Remove.PopFilter.ByClasses(types), isRemoveAll = false)

    /**
     * 按 === 移除指定实例，不携带业务结果
     *
     * Removes the exact instance by === without a business result.
     */
    fun last(instance: IRouterEntryProvider) =
        remove(PopType.Remove.PopFilter.ByInstance(instance), isRemoveAll = false)

    /**
     * 移除 URL 匹配类型的最近一页
     *
     * Removes the nearest page of the route class resolved by the URL.
     */
    fun last(url: String) = remove(PopType.Remove.PopFilter.ByUrl(url), isRemoveAll = false)

    /**
     * 移除指定类型的所有页面，不携带业务结果；全部命中时保留原根页。
     *
     * Removes all pages of the given class without a business result; retains the original root if all entries match.
     */
    fun all(type: KClass<*>) =
        remove(PopType.Remove.PopFilter.ByClasses(setOf(type)), isRemoveAll = true)

    /**
     * 移除所有 T 类型页面，全部命中时保留原根页
     *
     * Removes all T pages, retaining the root if all entries match.
     */
    inline fun <reified T : Any> all() = all(T::class)

    /**
     * 移除任一候选类型匹配的所有页面，全部命中时保留原根页。
     *
     * Removes all pages matching any candidate class, retaining the original root if all entries match.
     */
    fun all(vararg types: KClass<*>) =
        remove(PopType.Remove.PopFilter.ByClasses(types.toSet()), isRemoveAll = true)

    /**
     * 移除类型集合匹配的所有页面，保留非空栈
     *
     * Removes all pages whose class belongs to the set, retaining a nonempty stack.
     */
    fun all(types: Set<KClass<*>>) =
        remove(PopType.Remove.PopFilter.ByClasses(types), isRemoveAll = true)

    /**
     * 移除 URL 匹配类型的所有页面，全部命中时保留原根页；查询参数不参与匹配。
     *
     * Removes all pages of the URL's route class, retaining the root if all entries match; query parameters are ignored.
     */
    fun all(url: String) = remove(PopType.Remove.PopFilter.ByUrl(url), isRemoveAll = true)

    @PublishedApi
    internal fun backToWithResult(url: String, result: Any?, key: String, expectedType: KClass<*>) {
        backToWithResult(PopType.BackTo.PopDestination.ByUrl(url), result, key, expectedType)
    }

    @PublishedApi
    internal fun backToWithResult(clazz: KClass<*>, result: Any?, key: String, expectedType: KClass<*>) {
        backToWithResult(PopType.BackTo.PopDestination.ByClass(clazz), result, key, expectedType)
    }

    @PublishedApi
    internal fun backToWithResult(instance: IRouterEntryProvider, result: Any?, key: String, expectedType: KClass<*>) {
        backToWithResult(PopType.BackTo.PopDestination.ByInstance(instance), result, key, expectedType)
    }

    private fun backToWithResult(
        destination: PopType.BackTo.PopDestination,
        result: Any?,
        key: String,
        expectedType: KClass<*>,
    ) {
        backTo(destination, PopType.BackTo.DirectResult(PopResult.Provided(result), key, expectedType))
    }

    private fun backTo(
        dest: PopType.BackTo.PopDestination,
        directResult: PopType.BackTo.DirectResult? = null,
    ) {
        popType = PopType.BackTo(
            destination = dest,
            directResult = directResult,
        )
    }

    private fun remove(filter: PopType.Remove.PopFilter, isRemoveAll: Boolean) {
        popType = PopType.Remove(filter, isRemoveAll)
    }
}
