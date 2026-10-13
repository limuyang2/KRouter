package io.github.limuyang2.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * 使用 @Router 注解的类，可以通过实现此接口提供绑定的 Compose UI
 *
 * Implemented by @Router classes to provide their Compose UI, content identity and entry metadata.
 */
@Immutable
interface IRouterEntryProvider {

    /**
     * 绑定的 Compose UI
     *
     * Renders this route's Compose UI; the default implementation renders nothing.
     */
    @Composable
    fun UI() {}

    /**
     * 页面的 key，通常来说是一个唯一、稳定的 ID 来标识此 NavEntry 的内容。
     *
     * 如果两个 Page 页面需要共享 viewModel，可以通过设置相同的 key 来共享 viewModel。
     *
     * Returns a stable identity for this NavEntry's content scope, defaulting to toString().
     * Entries with the same key share content-scoped state, including ViewModels and result ownership;
     * use distinct keys when those scopes must be independent.
     */
    fun contentKey(): String = this.toString()

    /**
     * 页面元数据，例如可以指定页面动画、类型（是否是Dialog）
     *
     * Returns entry metadata used by scene strategies and transitions, for example a dialog marker.
     * Defaults to an empty map.
     */
    fun metadata(): Map<String, Any> = emptyMap()
}
