package io.github.limuyang2.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * 使用 @Router 注解的类，可以通过实现此接口提供绑定的 Compose UI
 */
@Immutable
interface IRouterEntryProvider {

    /**
     * 绑定的 Compose UI
     */
    @Composable
    fun UI() {}

    /**
     * 页面的 key，通常来说是一个唯一、稳定的 ID 来标识此 NavEntry 的内容。
     *
     * 如果两个 Page 页面需要共享 viewModel，可以通过设置相同的 key 来共享 viewModel。
     */
    fun contentKey(): String = this.toString()

    /**
     * 页面元数据，例如可以指定页面动画、类型（是否是Dialog）
     */
    fun metadata(): Map<String, Any> = emptyMap()
}
