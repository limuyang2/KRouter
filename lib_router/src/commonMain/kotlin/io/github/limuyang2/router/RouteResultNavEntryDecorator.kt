package io.github.limuyang2.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntryDecorator

/**
 * 页面内部 result API 需要的最小上下文。
 *
 * contentKey 来自 NavEntry，controller 来自同一个 Navigator/Core；这样页面内
 * rememberRouteResult 不需要再反向查 Navigator，也不会产生第二份 ResultStore。
 */
internal data class RouteResultOwner(
    val contentKey: Any,
    val controller: RouteResultController,
)

internal val LocalRouteResultOwner = compositionLocalOf<RouteResultOwner?> { null }

/**
 * 为 router result 系统提供和 Nav3 一致的 contentKey 作用域。
 *
 * 这个 decorator 应与 NavDisplay 的其它 entryDecorators 一起安装。
 * 为 [rememberRouteResult] 提供 entry 上下文，并在 Nav3 确认内容作用域移除后清理结果状态。
 *
 * Creates the entry decorator aligning route-result ownership with Nav3 contentKey scopes.
 * Install it in NavDisplay.entryDecorators alongside the other decorators. It provides the entry
 * context for [rememberRouteResult] and clears result state after Nav3 confirms scope removal.
 *
 * @param navigator 与 NavDisplay 使用同一导航栈的 Navigator，默认取 [LocalNavigator] / Navigator backing
 * the same NavDisplay stack; defaults to [LocalNavigator].
 * @return 为每个 entry 提供结果作用域的装饰器 / Decorator providing the result-owner scope for each entry.
 */
@Composable
fun <T : Any> rememberRouteResultNavEntryDecorator(
    navigator: Navigator = LocalNavigator.current,
): NavEntryDecorator<T> {
    val controller = navigator.routeResultController
    return remember(controller) {
        NavEntryDecorator(
            onPop = { contentKey ->
                // onPop 的时机由 Nav3 控制：最后一个同 contentKey 的 entry 离栈且动画 composition 结束。
                controller.dropOwnerState(contentKey)
            },
            decorate = { entry ->
                // 以 NavEntry.contentKey 作为 result owner，和 saveable/viewModel decorator 对齐。
                CompositionLocalProvider(
                    LocalRouteResultOwner provides RouteResultOwner(
                        contentKey = entry.contentKey,
                        controller = controller,
                    )
                ) {
                    entry.Content()
                }
            },
        )
    }
}
