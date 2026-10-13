package io.github.limuyang2.router

import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.flow.SharedFlow
import kotlin.reflect.KClass

/**
 * 对外暴露的路由导航入口。
 *
 * 结果回传统一使用 RouteResult 表达完整状态；Compose 页面可通过
 * `rememberRouteResult(...)` 创建 consumer token。
 *
 * Public navigation entry point. Results use [RouteResult]; Compose UI must receive them through
 * a consumer token created by [rememberRouteResult]. Push and pop operations run asynchronously.
 *
 * @param startDestination 初始路由 URL 或页面实例 / Initial route URL or destination instance.
 */
class Navigator(startDestination: Any) {

    private val core by lazy { Core(startDestination) }

    /**
     * 供 NavDisplay 观察；请通过 Navigator 修改栈，以保留实例限制和结果生命周期管理。
     *
     * Observed by NavDisplay; mutate through Navigator to preserve instance and result lifecycle rules.
     */
    val backStack: SnapshotStateList<IRouterEntryProvider> get() = core.mBackStack
    /**
     * 栈变更完成后的事件流
     *
     * Events emitted after back-stack mutations.
     */
    val backStackEvents: SharedFlow<BackStackEvent> get() = core.backStackEvents
    /**
     * 已注册模块的页面安装器
     *
     * Entry installers from the registered modules.
     */
    val pageProviderInstaller: List<EntryProviderInstaller> get() = core.pageProviderInstaller

    // 只暴露一个 internal runtime 给 decorator，避免 Navigator 增加 result 注册/注销桥接方法。
    internal val routeResultController: RouteResultController get() = core.routeResultController

    /**
     * 通过 DSL 异步发起跳转，可组合目标、参数、拦截器选项和结果接收方式。
     *
     * Asynchronously pushes using the DSL to configure a target, extras, interceptor options and results.
     *
     * @param builder 跳转配置 / Push configuration.
     */
    fun push(builder: PushBuilder.() -> Unit) {
        core.push(builder)
    }

    /**
     * 通过 URL 异步打开页面，解析查询参数；未匹配 URL 不会入栈。
     *
     * Asynchronously opens a URL and parses query parameters; unmatched URLs are not added to the stack.
     *
     * @param url 路由 URL / Route URL.
     */
    fun push(url: String) {
        core.push { this.url = url }
    }

    /**
     * 异步打开目标实例；同一对象不能重复入栈，不同实例可以拥有相同参数。
     *
     * Asynchronously opens an instance; the same object cannot be pushed twice, even with duplicates allowed.
     * Distinct instances may have identical parameters.
     *
     * @param destination 目标页面实例 / Destination instance.
     */
    fun push(destination: IRouterEntryProvider) {
        core.push { this.destination = destination }
    }

    /**
     * 禁止在 Compose UI 中使用此 callback API；Compose 页面应通过 [rememberRouteResult]
     * 创建 token，并使用 `pushForResult(destination, resultTo = token)` 接收结果。
     *
     * Do not use this callback API in Compose UI; create a token with [rememberRouteResult] and call
     * `pushForResult(destination, resultTo = token)` instead.
     *
     * 打开 [destination]，通过普通 callback 接收完整的 [RouteResult]。
     *
     * 请求持有发起跳转时传入的 [onResult] 闭包，结果派发时仍调用该闭包；
     * 页面重组或重建不会自动将它替换为最新的回调。
     * [DisallowComposableCalls] 禁止回调内调用 Composable，但不限制捕获或修改外部 Compose 状态。
     * 适用于非 Compose 场景，或更新 ViewModel、业务状态等不依赖页面 Composition 的对象。
     * 调用方需要保证回调捕获的对象在结果派发时仍然有效。
     *
     * Compose 中，同一 Composition 内普通重组时，位置和 key 未改变的 `remember` 状态
     * 仍是同一对象；但页面离开 Composition 后重新进入，或 `remember` 的 key 改变时，
     * 可能产生新的状态对象，旧回调仍会修改原来的状态。`rememberSaveable` 恢复的是状态值，
     * 不保证旧回调引用的是当前页面的状态对象。
     *
     * Compose 页面接收结果并更新局部状态时，推荐使用 [rememberRouteResult] 创建
     * [RouteResultConsumerToken]，再调用 `pushForResult(destination, resultTo = token)`，
     * 由当前 Composition 注册的最新 consumer 接收结果。
     *
     * Opens [destination] and receives the full [RouteResult] through the original callback closure.
     * Recomposition and page recreation do not replace that closure. Use it from non-Compose code to
     * update a ViewModel or business state whose lifetime does not depend on the page Composition;
     * captured objects must still be valid when the result is dispatched.
     * [DisallowComposableCalls] prevents composable calls inside the callback, not capture or mutation
     * of external Compose state. Ordinary recomposition retains `remember` objects with unchanged
     * positions and keys; leaving Composition or changing a key may create new objects while the
     * callback still references the old ones. `rememberSaveable` restores values, not the identity
     * of state captured by an old callback. Consumer tokens dispatch to the current Composition.
     *
     * @param destination 目标页面实例 / Destination instance.
     * @param onResult 发起时的结果回调，不随重组更新 / Original result callback, not refreshed by recomposition.
     */
    inline fun <reified R : Any> pushForResult(
        destination: IRouterEntryProvider,
        noinline onResult: @DisallowComposableCalls (RouteResult<R>) -> Unit,
    ) {
        push {
            this.destination = destination
            onRouteResult(onResult)
        }
    }

    /**
     * 禁止在 Compose UI 中使用此 callback API；Compose 页面应通过 [rememberRouteResult]
     * 创建 token，并使用 `pushForResult(url, resultTo = token)` 接收结果。
     *
     * Do not use this callback API in Compose UI; create a token with [rememberRouteResult] and call
     * `pushForResult(url, resultTo = token)` instead.
     *
     * 打开 [url]，通过普通 callback 接收完整的 [RouteResult]。
     *
     * 请求持有发起跳转时传入的 [onResult] 闭包，结果派发时仍调用该闭包；
     * 页面重组或重建不会自动将它替换为最新的回调。
     * [DisallowComposableCalls] 禁止回调内调用 Composable，但不限制捕获或修改外部 Compose 状态。
     * 适用于非 Compose 场景，或更新 ViewModel、业务状态等不依赖页面 Composition 的对象。
     * 调用方需要保证回调捕获的对象在结果派发时仍然有效。
     *
     * Compose 中，同一 Composition 内普通重组时，位置和 key 未改变的 `remember` 状态
     * 仍是同一对象；但页面离开 Composition 后重新进入，或 `remember` 的 key 改变时，
     * 可能产生新的状态对象，旧回调仍会修改原来的状态。`rememberSaveable` 恢复的是状态值，
     * 不保证旧回调引用的是当前页面的状态对象。
     *
     * Compose 页面接收结果并更新局部状态时，推荐使用 [rememberRouteResult] 创建
     * [RouteResultConsumerToken]，再调用 `pushForResult(url, resultTo = token)`，
     * 由当前 Composition 注册的最新 consumer 接收结果。
     *
     * Opens [url] and receives the full [RouteResult] through the original callback closure.
     * Recomposition and page recreation do not replace that closure. Use it from non-Compose code to
     * update a ViewModel or business state whose lifetime does not depend on the page Composition;
     * captured objects must still be valid when the result is dispatched.
     * [DisallowComposableCalls] prevents composable calls inside the callback, not capture or mutation
     * of external Compose state. Ordinary recomposition retains `remember` objects with unchanged
     * positions and keys; leaving Composition or changing a key may create new objects while the
     * callback still references the old ones. `rememberSaveable` restores values, not the identity
     * of state captured by an old callback. Consumer tokens dispatch to the current Composition.
     *
     * @param url 路由 URL / Route URL.
     * @param onResult 发起时的结果回调，不随重组更新 / Original result callback, not refreshed by recomposition.
     */
    inline fun <reified R : Any> pushForResult(
        url: String,
        noinline onResult: @DisallowComposableCalls (RouteResult<R>) -> Unit,
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
     *
     * Compose result API: opens [destination] and binds a consumer token instead of a callback closure.
     * The request stores the slot ID; the current consumer receives the result when its entry is foreground.
     *
     * @param destination 目标页面实例 / Destination instance.
     * @param resultTo 发起页面通过 [rememberRouteResult] 创建的 token / Token created by the caller entry.
     */
    fun <R : Any> pushForResult(destination: IRouterEntryProvider, resultTo: RouteResultConsumerToken<R>) {
        core.push {
            this.destination = destination
            resultTo(resultTo)
        }
    }

    /**
     * 通过 URL 跳转并绑定 Consumer；发起页回到前台后由当前 Composition 消费结果。
     *
     * Opens a URL with a consumer token; the current Composition consumes the result when the caller is foreground.
     *
     * @param url 路由 URL / Route URL.
     * @param resultTo 发起页面通过 [rememberRouteResult] 创建的 token / Token created by the caller entry.
     */
    fun <R : Any> pushForResult(url: String, resultTo: RouteResultConsumerToken<R>) {
        core.push {
            this.url = url
            resultTo(resultTo)
        }
    }

    /**
     * 异步关闭处理前绑定的当前页，向等待它的发起页结算 [RouteResult.NoResult]；根页不会移除。
     *
     * Asynchronously closes the bound current page and settles [RouteResult.NoResult] for its waiting caller.
     * The root page is retained.
     */
    fun pop() {
        core.pop()
    }

    /**
     * 异步关闭当前页并回传业务值；显式 null 表示 Success(null)，根页不会移除，接收端校验结果类型。
     *
     * Asynchronously closes the current page with a value; explicit null means Success(null).
     * The root page is retained, and the receiver validates the expected result type.
     *
     * @param result 业务返回值，可明确为 null / Business result, including explicit null.
     */
    fun pop(result: Any?) {
        core.pop { top(result) }
    }

    /**
     * 使用 DSL 异步选择返回目标或移除页面；目标在拦截前绑定，等待期间新增页面不会被误删。
     *
     * Asynchronously selects a back destination or removal targets through the DSL. Targets are bound
     * before interception, so pages pushed while waiting are not accidentally removed.
     *
     * @param builder 返回配置 / Pop configuration.
     */
    fun pop(builder: PopBuilder.() -> Unit) {
        core.pop(builder)
    }

    /**
     * 查询 URL 对应的已注册路由类型，不实例化页面也不修改导航栈；未匹配返回 null。
     *
     * Resolves a registered route class without instantiating a page or changing the stack; returns null if unmatched.
     *
     * @param url 路由 URL，查询参数不参与匹配 / Route URL; query parameters do not affect matching.
     */
    fun findClassByUrl(url: String): KClass<*>? = core.findClassByUrl(url)

    /**
     * 返回当前栈顶实例，空栈时为 null
     *
     * Returns the top instance, or null for an empty stack.
     */
    fun last(): IRouterEntryProvider? = core.getTop()

    /**
     * 跳转 DSL 配置
     *
     * Configuration for the push DSL.
     */
    class PushBuilder {
        /**
         * URL 目标；若 destination 非空则优先使用实例。
         *
         * URL destination; a non-null destination instance takes precedence.
         */
        var url: String? = null
        /**
         * 直接打开的页面实例。
         *
         * Destination instance to open directly.
         */
        var destination: IRouterEntryProvider? = null
        /**
         * 额外参数，同名项覆盖 URL 查询参数。
         *
         * Extra parameters overriding query parameters with the same name.
         */
        var extras: Map<String, Any>? = null
        /**
         * 通过实例去重检查后、实际入栈前调用的回调。
         *
         * Callback invoked after instance duplicate checks and before actual stack insertion.
         */
        var routeBeforeCallback: (() -> Unit)? = null
        /**
         * 成功入栈后调用的回调，不是业务结果接收器。
         *
         * Callback invoked after a successful push, not a business-result consumer.
         */
        var routeSuccessCallback: (() -> Unit)? = null
        /**
         * 跳转被中断、重复实例被拒绝或目标无法入栈时调用的回调。
         *
         * Callback for interrupted pushes, rejected duplicates or destinations that cannot be added.
         */
        var routeFailureCallback: (() -> Unit)? = null
        /**
         * 请求成功或失败后调用；解析阶段无法创建请求时不保证执行。
         *
         * Callback after request success or failure; not guaranteed when parsing cannot create a request.
         */
        var routeCompletedCallback: (() -> Unit)? = null
        /**
         * 阻止栈顶相同类型重复跳转；同一对象重复入栈始终被拒绝。
         *
         * Prevents same-class duplicates at the top; the same object is always rejected on a repeated push.
         */
        var preventDuplicates = false
        /**
         * 成功跳转后清除旧页面，仅保留新页面。
         *
         * Removes old entries after a successful push, retaining only the new page.
         */
        var isCleanStack = false
        /**
         * push 成功后关闭旧 current。
         *
         * 这不是 replace：
         * - 新页面不会继承旧页面的结果关系；
         * - 旧页面如果被上游等待结果，会在自己被移除时按 Cancelled 结算；
         * - 因此这里不能再同时绑定 onRouteResult / resultTo。
         *
         * Closes the previous current page only after a successful push. This is not result-chain
         * replacement: the new page inherits no pending result relationship, and a caller waiting
         * for the removed page receives Cancelled. Cannot be combined with onRouteResult or resultTo.
         */
        var isPopCurrent = false
        /**
         * 跳过 Push 拦截器，仍保留目标解析、实例去重和栈变更规则。
         *
         * Skips push interceptors while retaining resolution, instance uniqueness and stack-mutation rules.
         */
        var greenChannel = false

        internal var routeResultCallback: ((RouteResult<Any>) -> Unit)? = null
        internal var resultSlotId: String? = null
        internal var expectedResultType: KClass<*>? = null

        /**
         * 禁止在 Compose UI 中使用此 callback API；Compose 页面应通过 [rememberRouteResult]
         * 创建 token，并使用 [resultTo] 绑定结果接收器。
         *
         * Do not use this callback API in Compose UI; bind a [rememberRouteResult] token with [resultTo].
         *
         * DSL 下的完整结果 callback 入口。
         *
         * 请求持有调用时传入的 [block] 闭包，结果派发时仍调用该闭包，
         * 不会随页面重组或重建自动更新。适用于非 Compose 场景，或更新 ViewModel、业务状态
         * 等不依赖页面 Composition 的对象；调用方需要保证捕获的对象仍然有效。
         * [DisallowComposableCalls] 禁止回调内调用 Composable，但不限制捕获或修改外部 Compose 状态。
         *
         * Compose 页面离开 Composition 后重新进入，或 `remember` 的 key 改变时，
         * 旧闭包可能仍引用旧状态；`rememberSaveable` 恢复状态值也不能保证对象引用保持不变。
         * 更新 Compose 页面局部状态时，推荐通过 [rememberRouteResult] 创建 token，
         * 再使用 [resultTo] 绑定当前 Composition 的最新 consumer。
         *
         * DSL callback entry point that retains the original closure until result dispatch.
         * Recomposition and page recreation do not refresh it. Use it from non-Compose code to update
         * state independent of the page Composition, and keep captured objects valid.
         * [DisallowComposableCalls] prevents composable calls, not capture of external Compose state.
         * Leaving Composition or changing a `remember` key can make captured state obsolete;
         * `rememberSaveable` restores values without guaranteeing the same object reference.
         * Compose UI must use a token so the current consumer can be rebound.
         *
         * @param block 发起时的结果回调，不随重组更新 / Original result callback, not refreshed by recomposition.
         */
        inline fun <reified T : Any> onRouteResult(
            noinline block: @DisallowComposableCalls (RouteResult<T>) -> Unit,
        ) {
            bindRouteResultCallback(T::class) { result ->
                @Suppress("UNCHECKED_CAST")
                block(result as RouteResult<T>)
            }
        }

        /**
         * DSL 下的推荐入口：绑定结果消费 token。
         *
         * Binds a consumer token in the DSL; the current consumer receives the full [RouteResult].
         *
         * @param token 发起页通过 [rememberRouteResult] 创建的 token / Token created by the caller entry.
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

        /**
         * 禁止在 Compose UI 中使用此 callback API；Compose 页面应通过 [rememberRouteResult]
         * 创建 token，并使用 `destination(dest, resultTo = token)` 接收结果。
         *
         * Do not use this callback API in Compose UI; use `destination(dest, resultTo = token)` instead.
         *
         * 设置目标并绑定普通 callback；闭包生命周期与 [onRouteResult] 相同。
         *
         * Sets the destination and binds a callback with the same closure lifetime as [onRouteResult].
         *
         * @param dest 目标页面实例 / Destination instance.
         * @param block 发起时的结果回调 / Original result callback.
         */
        inline fun <reified R : Any> destination(
            dest: IRouterEntryProvider,
            noinline block: @DisallowComposableCalls (RouteResult<R>) -> Unit,
        ) {
            this.destination = dest
            onRouteResult(block)
        }

        /**
         * 设置目标实例并绑定 Consumer，适用于 Compose UI。
         *
         * Sets the destination and binds a consumer token for Compose UI.
         *
         * @param dest 目标页面实例 / Destination instance.
         * @param resultTo 发起页面的 Consumer token / Consumer token owned by the caller entry.
         */
        fun <R : Any> destination(dest: IRouterEntryProvider, resultTo: RouteResultConsumerToken<R>) {
            this.destination = dest
            resultTo(resultTo)
        }
    }

    /**
     * 返回 DSL 配置，默认执行 top()
     *
     * Pop DSL configuration; defaults to top().
     */
    class PopBuilder : PopScope() {
        /**
         * 跳过返回拦截器，仍遵守目标绑定和根页保护
         *
         * Skips pop interceptors, retaining target and root rules.
         */
        var greenChannel: Boolean = false
    }
}
