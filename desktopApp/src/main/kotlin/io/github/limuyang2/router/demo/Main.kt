package io.github.limuyang2.router.demo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    initDemoRouter()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Router Demo",
    ) {
        RouterApp()
    }
}
