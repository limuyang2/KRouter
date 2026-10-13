package io.github.limuyang2.router

import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.flow.SharedFlow
import kotlin.reflect.KClass

/**
 * 对外暴露的路由导航入口。
 *
 * 结果回传统一使用 RouteResult 表达完整状态；Compose 页面可通过
 * `rememberRouteResult(...)` 创建 consumer token。
 */
class Navigator(startDestination: Any) {

    private val core by lazy { Core(startDestination) }

    /** 供 NavDisplay 观察。栈变更应通过 Navigator，否则会绕过实例限制与结果/请求生命周期管理。 */
    val backStack: SnapshotStateList<IRouterEntryProvider> get() = core.mBackStack
    val backStackEvents: SharedFlow<BackStackEvent> get() = core.backStackEvents
    val pageProviderInstaller: List<EntryProviderInstaller> get() = core.pageProviderInstaller

    // 只暴露一个 internal runtime 给 decorator，避免 Navigator 增加 result 注册/注销桥接方法。
    internal val routeResultController: RouteResultController get() = core.routeResultController

    fun push(builder: PushBuilder.() -> Unit) {
        core.push(builder)
    }

    fun push(url: String) {
        core.push { this.url = url }
    }

    fun push(destination: IRouterEntryProvider) {
        core.push { this.destination = destination }
    }

    inline fun <reified R : Any> pushForResult(
        destination: IRouterEntryProvider,
        noinline onResult: (RouteResult<R>) -> Unit,
    ) {
        push {
            this.destination = destination
            onRouteResult(onResult)
        }
    }

    inline fun <reified R : Any> pushForResult(
        url: String,
        noinline onResult: (RouteResult<R>) -> Unit,
    ) {
        push {
            this.url = url
            onRouteResult(onResult)
        }
    }

    /**
     * Compose 推荐用法：结果绑定到 consumer token，而不是直接绑定 lambda。
     *
     * push 时只会记录 token 对应的 slotId；
     * pop 后由页面重新进入前台时的最新 consumer 来真正消费结果。
     */
    fun <R : Any> pushForResult(destination: IRouterEntryProvider, resultTo: RouteResultConsumerToken<R>) {
        core.push {
            this.destination = destination
            resultTo(resultTo)
        }
    }

    fun <R : Any> pushForResult(url: String, resultTo: RouteResultConsumerToken<R>) {
        core.push {
            this.url = url
            resultTo(resultTo)
        }
    }

    fun pop() {
        core.pop()
    }

    fun pop(result: Any?) {
        core.pop { top(result) }
    }

    fun pop(builder: PopBuilder.() -> Unit) {
        core.pop(builder)
    }

    fun findClassByUrl(url: String): KClass<*>? = core.findClassByUrl(url)

    fun last(): IRouterEntryProvider? = core.getTop()

    class PushBuilder {
        var url: String? = null
        var destination: IRouterEntryProvider? = null
        var extras: Map<String, Any>? = null
        var routeBeforeCallback: (() -> Unit)? = null
        var routeSuccessCallback: (() -> Unit)? = null
        var routeFailureCallback: (() -> Unit)? = null
        var routeCompletedCallback: (() -> Unit)? = null
        var preventDuplicates = false
        var isCleanStack = false
        /**
         * push 成功后关闭旧 current。
         *
         * 这不是 replace：
         * - 新页面不会继承旧页面的结果关系；
         * - 旧页面如果被上游等待结果，会在自己被移除时按 Cancelled 结算；
         * - 因此这里不能再同时绑定 onRouteResult / resultTo。
         */
        var isPopCurrent = false
        var greenChannel = false

        internal var routeResultCallback: ((RouteResult<Any>) -> Unit)? = null
        internal var resultSlotId: String? = null
        internal var expectedResultType: KClass<*>? = null

        /**
         * DSL 下的完整结果 callback 入口。
         */
        inline fun <reified T : Any> onRouteResult(noinline block: (RouteResult<T>) -> Unit) {
            bindRouteResultCallback(T::class) { result ->
                @Suppress("UNCHECKED_CAST")
                block(result as RouteResult<T>)
            }
        }

        /**
         * DSL 下的推荐入口：绑定结果消费 token。
         */
        fun <T : Any> resultTo(token: RouteResultConsumerToken<T>) {
            resultSlotId = token.slotId
            expectedResultType = token.expectedType
        }

        @PublishedApi
        internal fun bindRouteResultCallback(
            expectedType: KClass<*>?,
            callback: (RouteResult<Any>) -> Unit,
        ) {
            if (expectedType != null) {
                expectedResultType = expectedType
            }
            routeResultCallback = callback
        }

        inline fun <reified R : Any> destination(
            dest: IRouterEntryProvider,
            noinline block: (RouteResult<R>) -> Unit,
        ) {
            this.destination = dest
            onRouteResult(block)
        }

        fun <R : Any> destination(dest: IRouterEntryProvider, resultTo: RouteResultConsumerToken<R>) {
            this.destination = dest
            resultTo(resultTo)
        }
    }

    class PopBuilder : PopScope() {
        var greenChannel: Boolean = false
    }
}
