package io.github.limuyang2.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.reflect.KClass

/**
 * 路由返回结果的统一表示。
 *
 * 这里不再只用 `R?` 表达结果，因为 `null` 本身可能就是一个合法业务值。
 * 因此需要把“无结果”“取消”“类型不匹配”这些状态单独建模。
 *
 * Complete route-result state. A nullable business value alone cannot distinguish successful null,
 * absence of a result, cancellation and type mismatch.
 */
sealed interface RouteResult<out R : Any> {
    /**
     * 成功返回业务值，包含明确的 null
     *
     * Successfully returned business value, including explicit null.
     */
    data class Success<R : Any>(val value: R?) : RouteResult<R>
    /**
     * 页面未提供业务值
     *
     * The page returned without a business value.
     */
    data object NoResult : RouteResult<Nothing>
    /**
     * 等待结果的请求因页面移除而取消
     *
     * The pending result request was cancelled by page removal.
     */
    data object Cancelled : RouteResult<Nothing>
    /**
     * 返回值类型与请求声明不匹配
     *
     * The returned value does not match the declared result type.
     */
    data class TypeMismatch(
        val expected: KClass<*>?,
        val actual: KClass<*>?,
    ) : RouteResult<Nothing>

    /**
     * 仅提取 Success 的业务值；其他状态返回 null，无法据此区分 Success(null) 和失败状态。
     *
     * Extracts the business value from Success; all other states return null. This cannot distinguish
     * Success(null) from an unsuccessful state, so inspect [RouteResult] when the distinction matters.
     *
     * @return 成功业务值或 null / Successful business value, or null.
     */
    fun get(): R? {
        return when (this) {
            is Success -> value
            NoResult -> null
            Cancelled -> null
            is TypeMismatch -> null
        }
    }
}

/**
 * Compose 侧结果消费槽位的公开令牌。
 *
 * 这个 token 不是直接保存业务 lambda，而是保存一个稳定的 [slotId]。
 * push 时只会把 slotId 记录到请求里；页面回来后，当前 Composition 会重新把最新的 consumer
 * 绑定到同一个 slotId 上，从而避免“旧页面闭包”问题。
 *
 * Public token identifying a Compose result slot. Stores a stable slot ID rather than the business
 * closure; the current Composition rebinds its latest consumer when the entry returns.
 */
class RouteResultConsumerToken<R : Any> @PublishedApi internal constructor(
    internal val slotId: String,
    internal val expectedType: KClass<*>?,
)

/**
 * Compose 推荐入口：consumer token 模式。
 *
 * 这个入口保留完整 [RouteResult] 语义；如果只关心业务值，调用 [RouteResult.get] 即可。
 * 同一 entry 内相同 key 与结果类型使用同一槽位；需要独立接收器时应使用不同的 key。
 * 必须在安装 [rememberRouteResultNavEntryDecorator] 的 NavEntry 内调用。
 *
 * Creates the consumer token used by Compose result APIs and preserves the full [RouteResult].
 * Recomposition updates the consumer closure; leaving and re-entering Composition rebinds the slot.
 * The same key and result type identify the same slot within an entry; use different keys for
 * independent consumers. Requires a NavEntry decorated by [rememberRouteResultNavEntryDecorator].
 * Use [RouteResult.get] when only the business value matters.
 *
 * @param key 接收槽位名称；null 使用该结果类型的默认槽位 / Slot name; null uses the result type's default slot.
 * @param onResult 当前 Composition 的完整结果接收器 / Full-result consumer of the current Composition.
 * @return 传给 pushForResult 的 resultTo 参数的 token / Token passed as resultTo to pushForResult.
 * @throws IllegalArgumentException 当前没有结果 entry 上下文 / No decorated result-entry context is available.
 */
@Composable
inline fun <reified R : Any> rememberRouteResult(
    key: String? = null,
    noinline onResult: (RouteResult<R>) -> Unit,
): RouteResultConsumerToken<R> {
    return rememberRouteResultToken(key = key, expectedType = R::class) { result: RouteResult<R> ->
        onResult(result)
    }
}

@PublishedApi
@Composable
internal fun <R : Any> rememberRouteResultToken(
    key: String?,
    expectedType: KClass<*>?,
    onResult: (RouteResult<R>) -> Unit,
): RouteResultConsumerToken<R> {
    // Result owner 只能由 NavEntryDecorator 提供，避免页面组合期再通过 route 反查 back stack。
    val owner = requireNotNull(LocalRouteResultOwner.current) {
        "rememberRouteResult is only available inside a NavEntry decorated by rememberRouteResultNavEntryDecorator(). " +
            "Add rememberRouteResultNavEntryDecorator() to NavDisplay.entryDecorators."
    }
    val ownerContentKey = owner.contentKey
    val latestOnResult by rememberUpdatedState(onResult)
    // slotId 由当前 contentKey 作用域、可选 key、预期类型共同确定。
    // 这样同一个页面可以安全挂多个结果消费位，而不会互相覆盖。
    val slotId = remember(ownerContentKey, key, expectedType) {
        buildRouteResultSlotId(key, expectedType)
    }
    val token = remember(slotId, expectedType) {
        RouteResultConsumerToken<R>(slotId = slotId, expectedType = expectedType)
    }

    DisposableEffect(owner.controller, ownerContentKey, slotId) {
        // 页面进入 Composition 时，把“当前这次 Composition 的最新 consumer”
        // 绑定到当前 contentKey 作用域的 slot 上；注册细节由 controller 统一加锁处理。
        owner.controller.registerConsumer(
            ownerContentKey = ownerContentKey,
            slotId = slotId,
        ) { result ->
            @Suppress("UNCHECKED_CAST")
            latestOnResult(result as RouteResult<R>)
        }

        onDispose {
            // 页面离开 Composition 时注销绑定，避免 result runtime 持有失效 consumer。
            owner.controller.unregisterConsumer(ownerContentKey = ownerContentKey, slotId = slotId)
        }
    }

    return token
}

@PublishedApi
internal fun buildRouteResultSlotId(
    key: String?,
    expectedType: KClass<*>?,
): String {
    // 用类型 + key 组合出稳定槽位名。
    // 这里不把 route identity 拼进字符串里，是因为 route identity 已经由外层 entry 作用域限定。
    val typeKey = expectedType?.routeResultTypeKey() ?: "any"
    val suffix = key ?: "__default__"
    return "$typeKey#$suffix"
}

internal expect fun KClass<*>.routeResultTypeKey(): String
