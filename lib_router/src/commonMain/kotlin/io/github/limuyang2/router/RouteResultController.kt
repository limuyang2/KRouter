package io.github.limuyang2.router

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Compose result consumer 的运行时入口。
 *
 * ResultStore 仍由 Core 持有；这里封装协程、导航锁和 drain 时机，避免
 * rememberRouteResult 直接操作裸 store，也避免 Navigator 暴露一批桥接方法。
 */
internal class RouteResultController(
    private val resultStore: ResultStore,
    private val navigationLock: Mutex,
    private val scope: CoroutineScope,
    private val isOwnerForeground: (Any) -> Boolean,
) {
    fun registerConsumer(
        ownerContentKey: Any,
        slotId: String,
        consumer: ResultConsumer,
    ) {
        scope.launch {
            val actions = navigationLock.withLock {
                resultStore.bindConsumerSlot(ownerContentKey, slotId, consumer)
                // consumer 重新进入 Composition 时，立即消费之前 pop 阶段排队的结果。
                if (isOwnerForeground(ownerContentKey)) {
                    resultStore.drainDeliverableResults(ownerContentKey)
                } else emptyList()
            }
            actions.forEach { it() }
        }
    }

    fun unregisterConsumer(
        ownerContentKey: Any,
        slotId: String,
    ) {
        scope.launch {
            navigationLock.withLock {
                resultStore.unbindConsumerSlot(ownerContentKey, slotId)
            }
        }
    }

    fun dropOwnerState(ownerContentKey: Any) {
        scope.launch {
            navigationLock.withLock {
                // 由 NavEntryDecorator.onPop 触发；此时 Nav3 已确认该 contentKey 离开 composition。
                resultStore.dropOwnerState(ownerContentKey)
            }
        }
    }
}
