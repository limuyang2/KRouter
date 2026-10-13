package io.github.limuyang2.router.demo

import io.github.limuyang2.router.demo.generated.RouterAggregatedRegistry

internal actual fun registerAggregatedRouters() {
    RouterAggregatedRegistry.registerAll()
}
