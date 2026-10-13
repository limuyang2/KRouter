package io.github.limuyang2.router

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.github.limuyang2.router.info.ParseRouterInfo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass

private data class PushMutation(
    val isSuccess: Boolean,
    val isDuplicate: Boolean = false,
    val previousTopRouteForPopCurrent: IRouterEntryProvider? = null,
    val pendingEvents: List<BackStackEvent> = emptyList(),
)

private data class PopMutation(
    val pendingEvents: List<BackStackEvent>,
    val dispatchActions: List<() -> Unit>,
)

private fun IRouterEntryProvider.debugLabel(): String {
    return "${this::class.simpleName}(contentKey=${contentKey()})"
}

internal class Core(startDestination: Any) :
    CoroutineScope by CoroutineScope(Dispatchers.Main.immediate + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
        logE { "Router Error: ${throwable.message}" }
    }) {

    companion object {
        private const val MAX_POP_REDIRECTS = 16
        internal val masterKV: MutableMap<KClass<*>, ParseRouterInfo> = HashMap()
        internal val aliasesKV: MutableMap<String, KClass<*>> = HashMap()
    }

    internal val mBackStack: SnapshotStateList<IRouterEntryProvider> = mutableStateListOf(InitRoute)

    private val globalPushInterceptors: List<IRouterPushInterceptor>
    private val globalPopInterceptors: List<IRouterPopInterceptor>
    private val realPushInterceptor = RealPushInterceptor(this)
    private val activePopPlans = mutableSetOf<PopPlan>()
    private val resultStore = ResultStore()
    private val navigationLock = Mutex()
    // 唯一的 result runtime 入口。Compose 侧通过 decorator 拿到它，Core 侧直接使用同一个 store。
    internal val routeResultController = RouteResultController(
        resultStore = resultStore,
        navigationLock = navigationLock,
        scope = this,
        isOwnerForeground = { owner -> mBackStack.lastOrNull()?.contentKey() == owner },
    )

    private val _backStackEvents = MutableSharedFlow<BackStackEvent>(replay = 0, extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.SUSPEND)
    internal val backStackEvents: SharedFlow<BackStackEvent> get() = _backStackEvents.asSharedFlow()

    init {
        RouterSingleton.providers.forEach { provider ->
            masterKV.putAll(provider.routeMasterKV())
            aliasesKV.putAll(provider.routeAliasesKV())
        }

        globalPushInterceptors = RouterSingleton.providers
            .flatMap { it.globalPushInterceptors() }
            .sortedBy { it.priority }
            .map { it.interceptor }

        globalPopInterceptors = RouterSingleton.providers
            .flatMap { it.globalPopInterceptors() }
            .sortedBy { it.priority }
            .map { it.interceptor }

        push {
            isCleanStack = true
            if (startDestination is String) {
                url = startDestination
            } else {
                @Suppress("UNCHECKED_CAST")
                destination = startDestination as IRouterEntryProvider
            }
        }
    }

    val pageProviderInstaller
        get() = RouterSingleton.providers.map { it.entryProviderInstaller() }

    fun push(builder: Navigator.PushBuilder.() -> Unit) {
        push(Navigator.PushBuilder().apply(builder))
    }

    fun push(builder: Navigator.PushBuilder) {
        launch {
            executePush(builder)
        }
    }

    fun pop() {
        pop(Navigator.PopBuilder())
    }

    fun pop(builder: Navigator.PopBuilder.() -> Unit) {
        pop(Navigator.PopBuilder().apply(builder))
    }

    fun pop(builder: Navigator.PopBuilder) {
        launch {
            executePop(builder)
        }
    }

    fun findClassByUrl(url: String): KClass<*>? {
        val clearUri = url.toUriOrNull()?.buildUpon()?.clearQuery()?.build() ?: return null
        return aliasesKV[clearUri.toString()]
    }

    fun getTop(): IRouterEntryProvider? = mBackStack.lastOrNull()

    fun findClassByClearUri(uri: RouteUri): KClass<*>? {
        return aliasesKV[uri.toString()]
    }

    private fun isDuplicateDestination(destination: Any): Boolean {
        val lastDestination = mBackStack.lastOrNull()
        return lastDestination != null && lastDestination::class == destination::class
    }

    /**
     * push 的统一执行入口。
     *
     * 对外 API 仍然是异步 launch，但真正的执行逻辑集中在这里，
     * 方便和 executePop 共享同样的“解析请求 -> 修改栈 -> 派发副作用”结构。
     *
     * 注意 isPopCurrent：
     * - push 成功后，需要把旧页面移除；
     * - 这里必须在同一个协程里串行执行内部 pop，
     *   否则会因为额外 launch 产生时序漂移。
     */
    private suspend fun executePush(builder: Navigator.PushBuilder) {
        // isPopCurrent 意味着 push 成功后当前页会被关闭，此时当前页无法等待结果，直接拒绝。
        if (builder.isPopCurrent && shouldTrackResult(builder)) {
            logE {
                "路由跳转被拒绝：isPopCurrent=true 表示 push 成功后关闭当前页面，当前页面无法再等待结果，请移除 onRouteResult/resultTo 绑定。"
            }
            builder.routeFailureCallback?.invoke()
            builder.routeCompletedCallback?.invoke()
            return
        }

        val response = getPushResponse(builder) ?: return

        if (response.isInterrupted) {
            logE {
                "路由跳转被中断，uri: [${response.request.uri}] destination: [${response.request.destination?.let { it::class }}]"
            }
            response.routeFailureCallback?.invoke()
            response.routeCompletedCallback?.invoke()
            return
        }

        val mutation = navigationLock.withLock {
            applyPushLocked(response, builder)
        }

        if (mutation.isDuplicate) {
            response.routeFailureCallback?.invoke()
            response.routeCompletedCallback?.invoke()
            return
        }

        dispatchNavigationEffects(mutation.pendingEvents)

        if (mutation.isSuccess) {
            response.routeSuccessCallback?.invoke()

            if (response.isPopCurrent) {
                mutation.previousTopRouteForPopCurrent?.let { previousTop ->
                    executePop(
                        Navigator.PopBuilder().apply {
                            last(previousTop)
                            greenChannel = true
                        }
                    )
                }
            }
        } else {
            response.routeFailureCallback?.invoke()
        }

        response.routeCompletedCallback?.invoke()
    }

    /**
     * pop 的统一执行入口。
     *
     * 这里负责把“拦截器解析”和“真实出栈执行”拆开：
     * - 在拦截前解析并绑定页面，只有显式 redirect 才重新解析；
     * - applyPopLocked 负责在锁内修改真实栈状态；
     * - 最后再统一派发 back stack event / result dispatch。
     */
    private suspend fun executePop(builder: Navigator.PopBuilder) {
        val plans = mutableListOf<PopPlan>()
        try {
            var request = navigationLock.withLock { resolvePopRequestLocked(builder.popType) } ?: return
            plans += request.plan
            var redirects = 0
            while (true) {
                val response = if (builder.greenChannel) {
                    PopResponse.Allowed(request)
                } else {
                    getPopResponseWithInterceptor(request)
                }
                when (response) {
                    PopResponse.Interrupted -> return
                    is PopResponse.Redirected -> {
                        if (response.from.plan !== request.plan || ++redirects > MAX_POP_REDIRECTS) {
                            logE { "Pop 重定向被中止：请求不匹配或超过 $MAX_POP_REDIRECTS 次重定向。" }
                            return
                        }
                        request = navigationLock.withLock {
                            if (!isPopPlanValidLocked(request.plan)) return@withLock null
                            activePopPlans.remove(request.plan)
                            request.plan.isValid = false
                            resolvePopRequestLocked(response.popType)
                        } ?: return
                        plans += request.plan
                    }
                    is PopResponse.Allowed -> {
                        // 不能复用其它导航操作缓存的放行响应。
                        if (response.request.plan !== request.plan) return
                        val mutation = navigationLock.withLock {
                            applyPopLocked(response.request)
                        } ?: return
                        dispatchNavigationEffects(mutation.pendingEvents, mutation.dispatchActions)
                        return
                    }
                }
            }
        } finally {
            // 此处没有挂起点；Core 的导航协程均在 Main 上执行，取消时也能完成清理。
            plans.forEach { it.isValid = false }
            activePopPlans.removeAll(plans.toSet())
        }
    }

    /**
     * 在 navigationLock 内执行真正的 push 栈变更。
     *
     * 这个方法只负责同步修改内部状态，不直接做后续副作用派发；
     * 这样调用方可以决定何时发事件、何时继续串联后续 pop。
     */
    private fun applyPushLocked(
        response: PushResponse,
        builder: Navigator.PushBuilder,
    ): PushMutation {
        val instance = response.destination

        // 同一对象只能占据一个栈位置；不同实例即使 equals/contentKey 相同也允许入栈。
        if (mBackStack.any { it === instance }) {
            logI { "[${instance}] 跳转失败，该页面实例已在返回栈中！" }
            return PushMutation(isSuccess = false, isDuplicate = true)
        }

        if (response.preventDuplicates && isDuplicateDestination(instance)) {
            logI { "[${instance}] 跳转失败，栈顶页面类型已存在！" }
            return PushMutation(isSuccess = false, isDuplicate = true)
        }

        response.routeBeforeCallback?.invoke()
        val pendingEvents = mutableListOf<BackStackEvent>()
        val previousTopRoute = mBackStack.lastOrNull()
        val added = mBackStack.add(instance)

        if (!added || instance is UnknownRoute) {
            if (added) {
                mBackStack.removeAt(mBackStack.lastIndex)
            }
            logI {
                "路由跳转失败！uri: [${response.request.uri}] destination: [${response.request.destination?.let { it::class }}]"
            }
            return PushMutation(isSuccess = false)
        }

        when {
            response.isCleanStack -> {
                removeAllButTopLocked(pendingEvents)
            }

            // isPopCurrent 必须等 push 成功回调之后再串行执行 pop。
            response.isPopCurrent -> Unit

            previousTopRoute != null && shouldTrackResult(builder) -> {
                trackPushResultRequest(
                    builder = builder,
                    response = response,
                    callerContentKey = previousTopRoute.contentKey(),
                    targetContentKey = instance.contentKey(),
                )
            }
        }

        pendingEvents.add(BackStackEvent.Push(instance, mBackStack.toList()))
        logI { "路由跳转成功！[${response.destination::class}]" }
        return PushMutation(
            isSuccess = true,
            previousTopRouteForPopCurrent = if (response.isPopCurrent) previousTopRoute else null,
            pendingEvents = pendingEvents,
        )
    }

    /**
     * 在 navigationLock 内执行真正的 pop 栈变更。
     *
     * 这里会完成三件事：
     * - 把目标页面从栈里移除；
     * - 为被移除页面结算 request 链；
     * - 为新的栈顶 contentKey 收集可派发结果。
     */
    private fun applyPopLocked(request: PopRequest): PopMutation? {
        if (mBackStack.size <= 1) return null
        if (!isPopPlanValidLocked(request.plan)) return null
        val popType = request.popType

        val matchedIndices = request.plan.removeTargets
            .map { page -> mBackStack.indexOfFirst { it === page } }
            .sortedDescending()
        // 等待期间原根页面也可能被别的操作移除，提交时再次保护当前根页面。
        val indicesToRemove = if (matchedIndices.size == mBackStack.size) {
            matchedIndices.filter { it != 0 }
        } else matchedIndices
        if (indicesToRemove.isEmpty()) {
            logI { "pop失败：没有找到目标页面！[$popType]" }
            return null
        }

        val pendingEvents = mutableListOf<BackStackEvent>()
        val removedRoutes = indicesToRemove.map { index -> mBackStack[index] }
        val requestResultVictim = request.plan.requestResultVictim
            ?.takeIf { victim -> removedRoutes.any { it === victim } }

        indicesToRemove.forEach { index ->
            val victim = mBackStack.removeAt(index)
            invalidatePopPlansForRemovedPage(victim)
            val victimContentKey = victim.contentKey()
            val requestResultVictimContentKey = requestResultVictim?.contentKey()

            // 被连带移除的页面不能向 caller 报告 success，否则会伪造用户未发生的返回。
            // 相同 contentKey 代表同一个内容作用域，避免同批 remove 中的共享 owner 抢先 Cancelled。
            if (victimContentKey != requestResultVictimContentKey) {
                logI {
                    "result chain interrupted: removed=${victim.debugLabel()}, popType=$popType, settleAs=Cancelled"
                }
                deliverResultForRemovedEntry(
                    targetContentKey = victimContentKey,
                    routeResult = RouteResult.Cancelled,
                )
            }

            pendingEvents.add(BackStackEvent.Pop(victim, mBackStack.toList()))

            if (popType is PopType.Remove) {
                logI { "移除指定页面 [${victim::class.simpleName}]" }
            }
        }

        // 只有明确通过原 request 链返回的页面，才能结算 success/no-result。
        requestResultVictim?.let { victim ->
            val routeResult = when (popType) {
                is PopType.Current -> popResultToRouteResult(
                    popResult = popType.result,
                    expectedType = resultStore.expectedResultTypeOf(victim.contentKey()),
                )

                is PopType.Remove -> RouteResult.NoResult
                is PopType.BackTo -> error("BackTo direct result must not use request result path.")
            }
            logI {
                "result settled for removed entry: removed=${victim.debugLabel()}, popType=$popType, routeResult=${routeResult.debugLabel()}"
            }
            deliverResultForRemovedEntry(
                targetContentKey = victim.contentKey(),
                routeResult = routeResult,
            )
        }

        // BackTo 结果绑定解析时的接收页，不能误投给等待期间新入栈的页面。
        request.plan.backToPage?.let { receiver ->
            queueBackToDirectResultIfNeeded(popType, receiver.contentKey())
        }
        // 只消费实际栈顶的 inbox，后台接收页等待再次成为前台。
        val newTopRoute = mBackStack.lastOrNull()
        val dispatchActions = newTopRoute?.let { newTop ->
            when (popType) {
                is PopType.Current -> logI { "关闭当前页" }
                is PopType.BackTo -> logI { "向 [${request.backToPage?.let { it::class.simpleName }}] 回退，移除了 ${indicesToRemove.size} 个页面" }
                is PopType.Remove -> Unit
            }
            val actions = mutableListOf<() -> Unit>()
            val newTopContentKey = newTop.contentKey()
            actions += drainDeliverableResultsLocked(newTopContentKey)
            actions
        } ?: emptyList()

        return PopMutation(
            pendingEvents = pendingEvents,
            dispatchActions = dispatchActions,
        )
    }

    /**
     * cleanStack push 时，保留新栈顶，移除之前所有页面。
     */
    private fun removeAllButTopLocked(pendingEvents: MutableList<BackStackEvent>) {
        if (mBackStack.size <= 1) return

        val toRemove = mBackStack.subList(0, mBackStack.size - 1).toList()
        mBackStack.removeRange(0, mBackStack.size - 1)

        toRemove.forEach { victim ->
            invalidatePopPlansForRemovedPage(victim)
            deliverResultForRemovedEntry(
                targetContentKey = victim.contentKey(),
                routeResult = RouteResult.Cancelled,
            )
            pendingEvents.add(BackStackEvent.Pop(victim, mBackStack.toList()))
        }
    }

    /**
     * 记录“caller 等 target 返回结果”的一跳请求关系。
     *
     * 这里只登记 request 关系；结果派发延迟到 caller 重新回到前台后。
     */
    private fun trackPushResultRequest(
        builder: Navigator.PushBuilder,
        response: PushResponse,
        callerContentKey: Any,
        targetContentKey: Any,
    ) {
        val expectedType = resolveExpectedResultType(
            builder = builder,
        )
        resultStore.trackResultRequest(
            ResultRequestRecord(
                callerContentKey = callerContentKey,
                targetContentKey = targetContentKey,
                slotId = builder.resultSlotId,
                routeResultCallback = builder.routeResultCallback,
                expectedType = expectedType,
            )
        )
    }

    /**
     * 计算本次 pop 中，哪个被移除 route 通过原 request 链正常返回。
     *
     * Remove(removeAll=true) 可能一次移除多条 route；只有解析时栈顶那条被视为正常返回，
     * 其它连带移除项按 Cancelled 结算。removedRoutes 的顺序是栈顶 -> 栈底。
     *
     * BackTo 带结果时走目标页 direct result，不再借中间页面 request 链中转。
     */
    private fun resolveRequestResultVictim(
        popType: PopType,
        removedRoutes: List<IRouterEntryProvider>,
        isRemovingTop: Boolean,
    ): IRouterEntryProvider? {
        return when (popType) {
            is PopType.Current -> removedRoutes.lastOrNull()
            is PopType.BackTo -> null
            is PopType.Remove -> if (isRemovingTop) removedRoutes.firstOrNull() else null
        }
    }

    /**
     * BackTo 场景下，若携带了 DirectResult，直接向目标 contentKey 派发。
     * 走独立路径，不经过中间页面的 request 链。
     */
    private fun queueBackToDirectResultIfNeeded(
        popType: PopType,
        targetContentKey: Any,
    ) {
        if (popType !is PopType.BackTo) {
            return
        }

        val directResult = popType.directResult ?: return
        val routeResult = popResultToRouteResult(
            popResult = directResult.result,
            expectedType = directResult.expectedType,
        )
        val slotId = buildRouteResultSlotId(directResult.key, directResult.expectedType)
        resultStore.queueDirectResult(
            ownerContentKey = targetContentKey,
            slotId = slotId,
            routeResult = routeResult,
        )
    }

    /**
     * 统一派发出栈/入栈事件，以及 caller 回前台后的结果消费动作。
     */
    private fun dispatchNavigationEffects(
        pendingEvents: List<BackStackEvent>,
        dispatchActions: List<() -> Unit> = emptyList(),
    ) {
        pendingEvents.forEach { _backStackEvents.tryEmit(it) }
        dispatchActions.forEach { it() }
    }

    private suspend fun getPushResponse(builder: Navigator.PushBuilder): PushResponse? {
        val url = builder.url
        val destination = builder.destination

        return if (!url.isNullOrBlank()) {
            getPushResponseWithUrl(url, builder)
        } else if (destination != null) {
            getPushResponseWithClass(destination, builder)
        } else {
            logE { "路由跳转失败！请检查 url 或者 destination 是否为空" }
            null
        }
    }

    private suspend fun getPushResponseWithUrl(
        url: String,
        builder: Navigator.PushBuilder,
    ): PushResponse? {
        val uri = url.toUriOrNull() ?: return null
        val urlParams = uri.getUriParamsMap()
        val cleanUri = uri.buildUpon().clearQuery().build() ?: return null
        val mergedExtras = builder.extras?.let { urlParams + it } ?: urlParams

        val request = PushRequest(
            originalUri = uri,
            uri = cleanUri,
            extras = mergedExtras,
            destination = null,
            preventDuplicates = builder.preventDuplicates,
            isCleanStack = builder.isCleanStack,
            isPopCurrent = builder.isPopCurrent,
            routeBeforeCallback = builder.routeBeforeCallback,
            routeSuccessCallback = builder.routeSuccessCallback,
            routeFailureCallback = builder.routeFailureCallback,
            routeCompletedCallback = builder.routeCompletedCallback,
        )

        return if (builder.greenChannel) {
            getPushResponseWithGreenChannel(request)
        } else {
            getPushResponseWithInterceptor(request)
        }
    }

    private suspend fun getPushResponseWithClass(
        destination: IRouterEntryProvider,
        builder: Navigator.PushBuilder,
    ): PushResponse {
        val request = PushRequest(
            originalUri = null,
            uri = null,
            extras = builder.extras,
            destination = destination,
            preventDuplicates = builder.preventDuplicates,
            isCleanStack = builder.isCleanStack,
            isPopCurrent = builder.isPopCurrent,
            routeBeforeCallback = builder.routeBeforeCallback,
            routeSuccessCallback = builder.routeSuccessCallback,
            routeFailureCallback = builder.routeFailureCallback,
            routeCompletedCallback = builder.routeCompletedCallback,
        )

        return if (builder.greenChannel) {
            getPushResponseWithGreenChannel(request)
        } else {
            getPushResponseWithInterceptor(request)
        }
    }

    internal fun getRouterInfo(realUri: RouteUri?): ParseRouterInfo? {
        if (realUri == null) return null
        return aliasesKV[realUri.toString()]?.let { masterKV[it] }
    }

    private fun getRouterInfo(clazz: KClass<*>): ParseRouterInfo? {
        return masterKV[clazz]
    }

    /**
     * 根据 PushRequest 创建路由实例。
     * 优先使用已有的 destination；否则从 URL 解析出路由类再实例化。
     * 解析失败时返回 UnknownRoute。
     */
    internal fun getClassInstance(request: PushRequest): IRouterEntryProvider {
        if (request.destination != null) {
            return request.destination
        }

        val realUri = request.uri ?: return UnknownRoute()
        val routerInfo = getRouterInfo(realUri) ?: return UnknownRoute(uri = realUri)
        return routerInfo.instanceProvider.instance(request.extras)
    }

    /**
     * 走拦截器链处理 push 请求。
     * 支持 onRestart 重新发起整个拦截器链（例如拦截器修改了目标后需要重新走一遍）。
     */
    private suspend fun getPushResponseWithInterceptor(request: PushRequest): PushResponse {
        var currentRequest = request

        do {
            var restartChain = false

            val pageInterceptors = if (currentRequest.destination != null) {
                getRouterInfo(currentRequest.destination::class)?.pushInterceptors
            } else {
                getRouterInfo(currentRequest.uri)?.pushInterceptors
            }?.map { it.interceptor }

            val interceptors = ArrayList<IRouterPushInterceptor>(
                globalPushInterceptors.size + (pageInterceptors?.size ?: 0) + 1
            )

            interceptors += globalPushInterceptors
            pageInterceptors?.let { interceptors += it }
            interceptors += realPushInterceptor

            val chain = RealPushInterceptorChain(
                interceptors = interceptors,
                index = 0,
                request = currentRequest,
                core = this,
                onRestart = { newRequest ->
                    currentRequest = newRequest
                    restartChain = true
                }
            )

            val response = chain.proceed(currentRequest)
            if (restartChain) continue
            return response
        } while (restartChain)

        throw IllegalStateException("Interceptor chain finished without a response.")
    }

    private fun getPushResponseWithGreenChannel(request: PushRequest): PushResponse {
        return PushResponse(
            request = request,
            destination = getClassInstance(request),
            preventDuplicates = request.preventDuplicates,
            isCleanStack = request.isCleanStack,
            isPopCurrent = request.isPopCurrent,
            routeBeforeCallback = request.routeBeforeCallback,
            routeSuccessCallback = request.routeSuccessCallback,
            routeFailureCallback = request.routeFailureCallback,
            routeCompletedCallback = request.routeCompletedCallback,
        )
    }

    /** 在导航锁内解析具体实例；所有页面选择只在这里发生。 */
    private fun resolvePopRequestLocked(popType: PopType): PopRequest? {
        if (mBackStack.size <= 1) return null
        val indices = findRemovableIndices(popType)
        if (indices.isEmpty()) return null
        val pages = indices.map { mBackStack[it] }
        val receiver = if (popType is PopType.BackTo) {
            mBackStack.lastOrNull { matchDestination(it, popType.destination) } ?: return null
        } else null
        val plan = PopPlan(
            removeTargets = pages,
            backToPage = receiver,
            requestResultVictim = resolveRequestResultVictim(
                popType, pages, indices.first() == mBackStack.lastIndex,
            ),
        )
        activePopPlans += plan
        return PopRequest(popType, plan)
    }

    private fun isPopPlanValidLocked(plan: PopPlan): Boolean {
        if (!plan.isValid) return false
        val exists = plan.removeTargets.all { page -> mBackStack.any { it === page } } &&
            (plan.backToPage == null || mBackStack.any { it === plan.backToPage })
        if (!exists) plan.isValid = false
        return exists
    }

    private fun invalidatePopPlansForRemovedPage(page: IRouterEntryProvider) {
        activePopPlans.forEach { plan ->
            if (plan.backToPage === page || plan.removeTargets.any { it === page }) {
                plan.isValid = false
            }
        }
    }

    private suspend fun getPopResponseWithInterceptor(request: PopRequest): PopResponse {
        val pageInterceptors = request.plan.removeTargets
            .asSequence()
            .mapNotNull { page -> getRouterInfo(page::class) }
            .flatMap { routerInfo -> routerInfo.popInterceptors }
            .distinct()
            .sortedBy { it.priority }
            .map { it.interceptor }
            .toList()

        return RealPopInterceptorChain(
            interceptors = globalPopInterceptors + pageInterceptors,
            index = 0,
            request = request,
        ).proceed(request)
    }

    /**
     * 根据 PopType 计算需要从栈中移除的索引列表。
     * - Current：仅移除栈顶
     * - Remove：按 filter 匹配；若会清空栈，则保留操作前的根页面
     * - BackTo：从栈顶到目标页面之间的所有页面
     */
    private fun findRemovableIndices(popType: PopType): List<Int> {
        if (mBackStack.isEmpty()) return emptyList()

        return when (popType) {
            is PopType.Current -> listOf(mBackStack.lastIndex)
            is PopType.Remove -> {
                val indices = ArrayList<Int>()
                for (i in mBackStack.lastIndex downTo 0) {
                    if (matchFilter(mBackStack[i], popType.filter)) {
                        indices.add(i)
                        if (!popType.isRemoveAll) break
                    }
                }
                // 索引按降序排列；全部命中时去掉最后的 0，保留原根页面。
                // 拦截器选页和实际删除共用此结果，根页面不会被当成出栈页面。
                if (indices.size == mBackStack.size) indices.dropLast(1) else indices
            }

            is PopType.BackTo -> {
                val index = mBackStack.indexOfLast { matchDestination(it, popType.destination) }
                if (index in 0 until mBackStack.lastIndex) {
                    (mBackStack.lastIndex downTo index + 1).toList()
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun matchDestination(page: Any, dest: PopType.BackTo.PopDestination): Boolean {
        return when (dest) {
            is PopType.BackTo.PopDestination.ByInstance -> page === dest.instance
            is PopType.BackTo.PopDestination.ByClass -> page::class == dest.clazz
            is PopType.BackTo.PopDestination.ByUrl -> {
                val clazz = findClassByUrl(dest.url)
                clazz != null && page::class == clazz
            }
        }
    }

    private fun matchFilter(page: Any, filter: PopType.Remove.PopFilter): Boolean {
        return when (filter) {
            is PopType.Remove.PopFilter.ByInstance -> page === filter.instance
            is PopType.Remove.PopFilter.ByClasses -> page::class in filter.classes
            is PopType.Remove.PopFilter.ByUrl -> {
                val clazz = findClassByUrl(filter.url)
                clazz != null && page::class == clazz
            }
        }
    }

    private fun shouldTrackResult(builder: Navigator.PushBuilder): Boolean {
        return builder.routeResultCallback != null || builder.resultSlotId != null
    }

    private fun resolveExpectedResultType(builder: Navigator.PushBuilder): KClass<*>? {
        return builder.expectedResultType
    }

    /**
     * 将 DSL 层的 PopResult 转换为统一的 RouteResult。
     * 如果实际值类型与期望类型不兼容，返回 TypeMismatch。
     */
    private fun popResultToRouteResult(
        popResult: PopResult,
        expectedType: KClass<*>?,
    ): RouteResult<Any> {
        return when (popResult) {
            PopResult.None -> RouteResult.NoResult
            is PopResult.Provided -> {
                val value = popResult.value
                val actualType = value?.let { it::class }
                // 这里必须用 isInstance，而不能用 `value::class == expectedType`。
                // 否则 Any、父类、接口这类本应兼容的声明类型都会被误判成 TypeMismatch。
                val isTypeMismatch = value != null &&
                    expectedType != null &&
                    !expectedType.isInstance(value)

                if (isTypeMismatch) {
                    RouteResult.TypeMismatch(
                        expected = expectedType,
                        actual = actualType,
                    )
                } else {
                    RouteResult.Success(value)
                }
            }
        }
    }

    private fun deliverResultForRemovedEntry(
        targetContentKey: Any,
        routeResult: RouteResult<Any>,
    ) {
        resultStore.deliverResultToCaller(targetContentKey, routeResult) { callerContentKey ->
            mBackStack.any { it.contentKey() == callerContentKey }
        }
    }

    private fun drainDeliverableResultsLocked(
        ownerContentKey: Any,
    ): List<() -> Unit> {
        return resultStore.drainDeliverableResults(ownerContentKey)
    }

}
