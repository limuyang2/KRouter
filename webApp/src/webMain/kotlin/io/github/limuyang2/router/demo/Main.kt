package io.github.limuyang2.router.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initDemoRouter()
    ComposeViewport {
        RouterApp()
    }
}
