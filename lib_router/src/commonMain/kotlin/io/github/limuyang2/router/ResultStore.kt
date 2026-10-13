package io.github.limuyang2.router

import kotlin.reflect.KClass

/**
 * 一次“等待返回结果”的请求记录。
 *
 * caller push target 时会登记一条 request：
 * - callerContentKey：谁在等结果；
 * - targetContentKey：哪个页面关闭时会产出结果；
 * - slotId：Compose token 模式下应该派发到哪个结果槽位；
 * - routeResultCallback：callback 模式下应该如何消费完整 RouteResult。
 */
internal data class ResultRequestRecord(
    val callerContentKey: Any,
    val targetContentKey: Any,
    val slotId: String?,
    val routeResultCallback: ((RouteResult<Any>) -> Unit)?,
    val expectedType: KClass<*>?,
)

/**
 * 已返回给 caller、但尚未被 caller 消费的结果。
 *
 * 这是“结果 inbox”里的元素：
 * - pop 时只负责把结果放进 inbox；
 * - caller 回到前台后，再从 inbox 派发给当前可用 consumer。
 */
private data class PendingResultRecord(
    val slotId: String?,
    val routeResultCallback: ((RouteResult<Any>) -> Unit)?,
    val routeResult: RouteResult<Any>,
)

internal typealias ResultConsumer = (RouteResult<Any>) -> Unit

/**
 * 某个 contentKey 自己持有的结果状态。
 *
 * 这里把同一个 contentKey 维度下的两类数据放在一起：
 * 1. 已注册的消费槽位；
 * 2. 已经返回但尚未消费的结果 inbox。
 */
private data class OwnerResultState(
    // 槽位声明随 entry 存活；null 表示该页面暂时离开 Composition，不能保留旧闭包。
    val consumerSlots: MutableMap<String, ResultConsumer?> = mutableMapOf(),
    val pendingResults: ArrayDeque<PendingResultRecord> = ArrayDeque(),
)

/**
 * 路由结果子系统的集中状态仓库。
 *
 * Core 只负责在合适的导航时机调用它：
 * - push 时登记 request；
 * - pop 时完成 request；
 * - owner 恢复前台时触发 dispatch；
 * - NavEntry contentKey 被 Nav3 确认 pop 后做清理。
 */
internal class ResultStore {
    private val requestsByTargetContentKey = mutableMapOf<Any, ResultRequestRecord>()
    private val ownerStates = mutableMapOf<Any, OwnerResultState>()

    fun trackResultRequest(request: ResultRequestRecord) {
        requestsByTargetContentKey[request.targetContentKey] = request
        logI {
            "result request tracked: callerContentKey=${request.callerContentKey}, targetContentKey=${request.targetContentKey}, slotId=${request.slotId}, expectedType=${request.expectedType}"
        }
    }

    fun expectedResultTypeOf(targetContentKey: Any): KClass<*>? {
        return requestsByTargetContentKey[targetContentKey]?.expectedType
    }

    fun bindConsumerSlot(
        ownerContentKey: Any,
        slotId: String,
        consumer: ResultConsumer,
    ) {
        val state = ownerStates.getOrPut(ownerContentKey) { OwnerResultState() }
        state.consumerSlots[slotId] = consumer
    }

    fun unbindConsumerSlot(
        ownerContentKey: Any,
        slotId: String,
    ) {
        val state = ownerStates[ownerContentKey] ?: return
        if (slotId in state.consumerSlots) {
            state.consumerSlots[slotId] = null
        }
        removeOwnerStateIfEmpty(ownerContentKey, state)
    }

    fun deliverResultToCaller(
        targetContentKey: Any,
        routeResult: RouteResult<Any>,
        callerExists: (Any) -> Boolean,
    ) {
        val request = requestsByTargetContentKey.remove(targetContentKey) ?: return
        if (!callerExists(request.callerContentKey)) {
            logI {
                "result dropped: targetContentKey=${request.targetContentKey}, callerContentKey=${request.callerContentKey}, routeResult=${routeResult.debugLabel()}, reason=caller_not_in_back_stack"
            }
            return
        }

        val pending = PendingResultRecord(
            slotId = request.slotId,
            routeResultCallback = request.routeResultCallback,
            routeResult = routeResult,
        )
        val state = ownerStates.getOrPut(request.callerContentKey) { OwnerResultState() }
        state.pendingResults.addLast(pending)
        logI {
            "result queued: ownerContentKey=${request.callerContentKey}, targetContentKey=${request.targetContentKey}, routeResult=${routeResult.debugLabel()}, slotId=${request.slotId}, callback=${request.routeResultCallback != null}"
        }
    }

    fun queueDirectResult(
        ownerContentKey: Any,
        slotId: String,
        routeResult: RouteResult<Any>,
    ) {
        val state = ownerStates[ownerContentKey]
        if (state == null || slotId !in state.consumerSlots) {
            logI {
                "direct result dropped: ownerContentKey=$ownerContentKey, slotId=$slotId, routeResult=${routeResult.debugLabel()}, reason=consumer_not_registered"
            }
            return
        }

        logI {
            "direct result queued: ownerContentKey=$ownerContentKey, slotId=$slotId, routeResult=${routeResult.debugLabel()}"
        }
        // 后台 entry 的 consumer 可能已注销，仍保留结果等待它重新绑定。
        // 保留 direct result 优先于已有 inbox 的顺序，消费时再取当前注册的 consumer。
        state.pendingResults.addFirst(
            PendingResultRecord(slotId, null, routeResult)
        )
    }

    fun dropOwnerState(
        ownerContentKey: Any,
        clearPendingRequest: Boolean = true,
    ) {
        ownerStates.remove(ownerContentKey)
        if (clearPendingRequest) {
            requestsByTargetContentKey.remove(ownerContentKey)
        }
    }

    fun drainDeliverableResults(ownerContentKey: Any): List<() -> Unit> {
        val state = ownerStates[ownerContentKey] ?: return emptyList()
        if (state.pendingResults.isEmpty()) return emptyList()

        val actions = mutableListOf<() -> Unit>()
        val remaining = ArrayDeque<PendingResultRecord>()

        while (state.pendingResults.isNotEmpty()) {
            val pending = state.pendingResults.removeFirst()
            val slot = pending.slotId?.let { state.consumerSlots[it] }
            val action = when {
                slot != null -> {
                    { slot(pending.routeResult) }
                }

                pending.routeResultCallback != null -> {
                    { pending.routeResultCallback.invoke(pending.routeResult) }
                }

                else -> null
            }

            if (action == null) {
                remaining.addLast(pending)
            } else {
                logI {
                    "result consumed: ownerContentKey=$ownerContentKey, routeResult=${pending.routeResult.debugLabel()}, slotId=${pending.slotId}, via=${if (slot != null) "consumer_slot" else "callback"}"
                }
                actions += action
            }
        }

        state.pendingResults.addAll(remaining)
        removeOwnerStateIfEmpty(ownerContentKey, state)
        return actions
    }

    private fun removeOwnerStateIfEmpty(
        ownerContentKey: Any,
        state: OwnerResultState,
    ) {
        if (state.consumerSlots.isEmpty() && state.pendingResults.isEmpty()) {
            ownerStates.remove(ownerContentKey)
        }
    }
}

internal fun RouteResult<Any>.debugLabel(): String {
    return when (this) {
        is RouteResult.Success -> "Success(value=$value)"
        RouteResult.NoResult -> "NoResult"
        RouteResult.Cancelled -> "Cancelled"
        is RouteResult.TypeMismatch -> "TypeMismatch(expected=$expected, actual=$actual)"
    }
}
