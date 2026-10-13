package io.github.limuyang2.router.demo

import io.github.limuyang2.router.Navigator
import io.github.limuyang2.router.initRouter

private var demoNavigator: Navigator? = null

val navigator: Navigator
    get() = requireDemoNavigator()

fun initDemoRouter() {
    initRouter {
        defaultScheme("test")
        isLog(true)
    }
    // 由宿主聚合插件生成的 RouterAggregatedRegistry 统一注册所有模块，
    // Android/Desktop/iOS/Web 一致，不再依赖 ServiceLoader 或手动注册。
    registerAggregatedRouters()
}

internal expect fun registerAggregatedRouters()

private fun createDemoNavigator(): Navigator = Navigator("test://test/home")


fun requireDemoNavigator(): Navigator {
    return demoNavigator ?: createDemoNavigator().also { demoNavigator = it }
}
