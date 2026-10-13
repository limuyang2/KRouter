package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import io.github.limuyang2.router.LocalNavigator
import io.github.limuyang2.router.demo.ui.theme.LibraryrouterTheme
import io.github.limuyang2.router.rememberRouteResultNavEntryDecorator

@Composable
fun RouterApp() {
    val navigator = remember { requireDemoNavigator() }
    val pageEntryProvider = remember { navigator.pageProviderInstaller }
    val sceneStrategies = remember {
        listOf(SinglePaneSceneStrategy<Any>())
    }

    LibraryrouterTheme {
        CompositionLocalProvider(LocalNavigator provides navigator) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                NavDisplay(
                    backStack = navigator.backStack,
                    onBack = { navigator.pop() },
                    sceneStrategies = sceneStrategies,
                    entryDecorators = listOf(
                        rememberRouteResultNavEntryDecorator(),
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator()
                    ),
                    entryProvider = entryProvider(fallback = {
                        NavEntry(
                            key = it,
                            content = { route -> FallbackPage(route) }
                        )
                    }) {
                        pageEntryProvider.forEach { it(this) }
                    },
                )
            }
        }
    }
}
