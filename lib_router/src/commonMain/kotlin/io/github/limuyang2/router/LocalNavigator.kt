package io.github.limuyang2.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf

/**
 * 用于在指定的 Composable 所在的环境内提供一个 [Navigator]
 *
 * Provides a [Navigator] to a Composition subtree.
 */
object LocalNavigator {
    private val LocalNavigatorClass: ProvidableCompositionLocal<Navigator?> =
        compositionLocalOf { null }

    /**
     * 获取当前 Navigator；未提供时创建以空 URL 初始化的新实例，应显式提供导航器。
     *
     * Returns the current Navigator; when absent, creates a new instance with an empty start URL.
     * Explicitly provide the Navigator for a working navigation and result context.
     */
    val current: Navigator
        @Composable
        get() = LocalNavigatorClass.current ?: Navigator("")

    /**
     * 创建供 CompositionLocalProvider 使用的导航器绑定。
     *
     * Creates a Navigator binding for CompositionLocalProvider.
     *
     * @param navigator 子树使用的导航器 / Navigator used by the subtree.
     * @return CompositionLocalProvider 所需的绑定值 / Binding value consumed by CompositionLocalProvider.
     */
    infix fun provides(
        navigator: Navigator,
    ): ProvidedValue<Navigator?> {
        return LocalNavigatorClass.provides(navigator)
    }
}
