package io.github.limuyang2.router.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

@Composable
fun DemoBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val dispatcherOwner = LocalNavigationEventDispatcherOwner.current

    DisposableEffect(Unit) {
        val dispatcher = dispatcherOwner?.navigationEventDispatcher

        val input = object : NavigationEventHandler<NavigationEventInfo>(
            NavigationEventInfo.None, true, true) {

            override fun onBackStarted(event: NavigationEvent) {
            }

            override fun onBackCompleted() {
                onBack()
            }

            override fun onBackCancelled() {
            }
        }

        dispatcher?.addHandler(input)

        onDispose {
            input.remove()
        }

    }
}
