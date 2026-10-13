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
 */
sealed interface RouteResult<out R : Any> {
    data class Success<R : Any>(val value: R?) : RouteResult<R>
    data object NoResult : RouteResult<Nothing>
    data object Cancelled : RouteResult<Nothing>
    data class TypeMismatch(
        val expected: KClass<*>?,
        val actual: KClass<*>?,
    ) : RouteResult<Nothing>

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
 */
class RouteResultConsumerToken<R : Any> @PublishedApi internal constructor(
    internal val slotId: String,
    internal val expectedType: KClass<*>?,
)

/**
 * Compose 推荐入口：consumer token 模式。
 *
 * 这个入口保留完整 [RouteResult] 语义；如果只关心业务值，调用 [RouteResult.get] 即可。
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
    val typeKey = expectedType?.qualifiedName ?: "any"
    val suffix = key ?: "__default__"
    return "$typeKey#$suffix"
}
