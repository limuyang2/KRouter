package io.github.limuyang2.router

import kotlin.reflect.KClass

internal sealed interface PopType {
    data class Current(val result: PopResult = PopResult.None) : PopType

    data class Remove(
        val filter: PopFilter,
        val isRemoveAll: Boolean
    ) : PopType {
        sealed interface PopFilter {
            data class ByInstance(val instance: IRouterEntryProvider) : PopFilter
            data class ByClasses(val classes: Set<KClass<*>>) : PopFilter
            data class ByUrl(val url: String) : PopFilter
        }
    }

    data class BackTo(
        val destination: PopDestination,
        val directResult: DirectResult? = null,
    ) : PopType {
        data class DirectResult(
            val result: PopResult.Provided,
            val key: String,
            val expectedType: KClass<*>,
        )

        sealed interface PopDestination {
            data class ByInstance(val instance: IRouterEntryProvider) : PopDestination
            data class ByClass(val clazz: KClass<*>) : PopDestination
            data class ByUrl(val url: String) : PopDestination
        }
    }
}

internal sealed interface PopResult {
    data object None : PopResult
    data class Provided(val value: Any?) : PopResult
}

/** 比较页面选择意图，不比较结果值；实例选择必须按引用比较。 */
internal fun PopType.sameSelectionAs(other: PopType): Boolean = when {
    this is PopType.Current && other is PopType.Current -> true
    this is PopType.Remove && other is PopType.Remove ->
        isRemoveAll == other.isRemoveAll && when (val selection = filter) {
            is PopType.Remove.PopFilter.ByInstance ->
                other.filter is PopType.Remove.PopFilter.ByInstance && selection.instance === other.filter.instance
            else -> selection == other.filter
        }
    this is PopType.BackTo && other is PopType.BackTo -> when (val selection = destination) {
        is PopType.BackTo.PopDestination.ByInstance ->
            other.destination is PopType.BackTo.PopDestination.ByInstance && selection.instance === other.destination.instance
        else -> selection == other.destination
    }
    else -> false
}
