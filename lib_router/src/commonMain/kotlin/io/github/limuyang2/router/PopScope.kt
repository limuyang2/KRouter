package io.github.limuyang2.router

import kotlin.reflect.KClass

/** Pop 和拦截器重定向共用的页面选择 DSL；仅表达意图，由 Core 解析具体页面。 */
open class PopScope internal constructor() {
    internal var popType: PopType = PopType.Current()

    /**
     * 绑定拦截前的栈顶，放行后关闭该实例并返回 NoResult。
     */
    fun top() {
        popType = PopType.Current()
    }

    /**
     * 绑定拦截前的栈顶并明确回传结果，哪怕 result 为 null，也会按 Success(null) 处理。
     */
    fun top(result: Any?) {
        popType = PopType.Current(PopResult.Provided(result))
    }

    fun to(url: String) {
        backTo(PopType.BackTo.PopDestination.ByUrl(url))
    }

    inline fun <reified R : Any> to(url: String, result: R?, key: String) {
        backToWithResult(url, result, key, R::class)
    }

    fun to(clazz: KClass<*>) {
        backTo(PopType.BackTo.PopDestination.ByClass(clazz))
    }

    inline fun <reified T : Any> to() = to(T::class)

    inline fun <reified R : Any> to(clazz: KClass<*>, result: R?, key: String) {
        backToWithResult(clazz, result, key, R::class)
    }

    inline fun <reified D : Any, reified R : Any> to(result: R?, key: String) {
        backToWithResult(D::class, result, key, R::class)
    }

    fun to(instance: IRouterEntryProvider) {
        backTo(PopType.BackTo.PopDestination.ByInstance(instance))
    }

    inline fun <reified R : Any> to(instance: IRouterEntryProvider, result: R?, key: String) {
        backToWithResult(instance, result, key, R::class)
    }

    fun remove(url: String) = remove(PopType.Remove.PopFilter.ByUrl(url), isRemoveAll = false)

    fun last(type: KClass<*>) =
        remove(PopType.Remove.PopFilter.ByClasses(setOf(type)), isRemoveAll = false)

    inline fun <reified T : Any> last() = last(T::class)

    fun last(vararg types: KClass<*>) =
        remove(PopType.Remove.PopFilter.ByClasses(types.toSet()), isRemoveAll = false)

    fun last(types: Set<KClass<*>>) =
        remove(PopType.Remove.PopFilter.ByClasses(types), isRemoveAll = false)

    fun last(instance: IRouterEntryProvider) =
        remove(PopType.Remove.PopFilter.ByInstance(instance), isRemoveAll = false)

    fun last(url: String) = remove(PopType.Remove.PopFilter.ByUrl(url), isRemoveAll = false)

    fun all(type: KClass<*>) =
        remove(PopType.Remove.PopFilter.ByClasses(setOf(type)), isRemoveAll = true)

    inline fun <reified T : Any> all() = all(T::class)

    fun all(vararg types: KClass<*>) =
        remove(PopType.Remove.PopFilter.ByClasses(types.toSet()), isRemoveAll = true)

    fun all(types: Set<KClass<*>>) =
        remove(PopType.Remove.PopFilter.ByClasses(types), isRemoveAll = true)

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
