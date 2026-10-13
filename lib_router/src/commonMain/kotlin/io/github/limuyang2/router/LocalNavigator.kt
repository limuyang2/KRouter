package io.github.limuyang2.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf

/**
 * 用于在指定的 Composable 所在的环境内提供一个 [Navigator]
 */
object LocalNavigator {
    private val LocalNavigatorClass: ProvidableCompositionLocal<Navigator?> =
        compositionLocalOf { null }

    /**
     * The current [Navigator]
     */
    val current: Navigator
        @Composable
        get() = LocalNavigatorClass.current ?: Navigator("")

    /**
     * Provides a [Navigator] to the composition
     */
    infix fun provides(
        navigator: Navigator,
    ): ProvidedValue<Navigator?> {
        return LocalNavigatorClass.provides(navigator)
    }
}