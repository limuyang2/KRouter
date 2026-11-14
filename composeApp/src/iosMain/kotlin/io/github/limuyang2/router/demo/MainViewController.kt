package io.github.limuyang2.router.demo

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    // Navigator 构造即同步解析初始路由，必须先完成 initRouter 与模块注册。
    initDemoRouter()
    return ComposeUIViewController { RouterApp() }
}
