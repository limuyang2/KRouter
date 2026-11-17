package io.github.limuyang2.router

import io.github.limuyang2.router.info.ParseRouterInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * singleTask 是「栈中已有则复用拉顶」的一步原子语义：按类型（URL push）或 ===（实例 push）
 * 命中已有实例后，上方非受保护页面按 Cancelled 结算移除，目标成为最顶非受保护页面；
 * 未命中退化为普通 push；组合冲突按合并后的生效值（builder 显式 > 注解默认 > false）拒绝。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SingleTaskTest {
    private class Root : IRouterEntryProvider
    private class Single : IRouterEntryProvider
    private class Plain : IRouterEntryProvider
    private class Gate : IRouterEntryProvider
    private class Protected : IRouterEntryProvider

    @Test
    fun urlPushMovesExistingInstanceToTopAndSettlesAboveAsCancelled() = withRouter { navigator, root ->
        val events = mutableListOf<BackStackEvent>()
        backgroundScope.launch { navigator.backStackEvents.collect { events += it } }
        var factoryCount = 0
        registerSingleRoute(Single::class, singleTask = false) { factoryCount++; Single() }
        val s1 = Single()
        navigator.push { destination = s1 }
        navigator.push { destination = Plain() }
        navigator.push { destination = Plain() }
        runCurrent()
        events.clear()

        navigator.push { url = SINGLE_URL; singleTask() }
        runCurrent()

        // 已有实例被复用并回到栈顶，上方夹层全部移除；工厂新实例被丢弃。
        assertEquals(2, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertSame(s1, navigator.last())
        assertEquals(1, factoryCount)
        // 夹层按移除序派发 Pop 事件，最后是目标复用的 Push 事件。
        val classes = events.map { it::class.simpleName }
        assertEquals(listOf("Pop", "Pop", "Push"), classes)
        assertSame(s1, (events.last() as BackStackEvent.Push).entry)
    }

    @Test
    fun multipleSameClassInstancesReuseTheTopmostOne() = withRouter { navigator, root ->
        registerSingleRoute(Single::class, singleTask = false) { Single() }
        val s1 = Single()
        val s2 = Single()
        navigator.push { destination = s1 }
        navigator.push { destination = s2 }
        navigator.push { destination = Plain() }
        runCurrent()

        navigator.push { url = SINGLE_URL; singleTask() }
        runCurrent()

        // 取最靠上的同类型实例：s2 之上的 Plain 被移除，s1 保留在下方。
        assertEquals(3, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertSame(s1, navigator.backStack[1])
        assertSame(s2, navigator.last())
    }

    @Test
    fun instancePushReusesThatExactInstance() = withRouter { navigator, root ->
        val s1 = Single()
        navigator.push { destination = s1 }
        navigator.push { destination = Plain() }
        runCurrent()

        navigator.push { destination = s1; singleTask() }
        runCurrent()

        assertEquals(2, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertSame(s1, navigator.last())
    }

    @Test
    fun instancePushWithNewInstanceNeverReusesAnotherSameClassInstance() = withRouter { navigator, _ ->
        val s1 = Single()
        navigator.push { destination = s1 }
        runCurrent()

        val s2 = Single()
        navigator.push { destination = s2; singleTask() }
        runCurrent()

        // === 未命中不复用 s1，按普通 push 叠加新实例。
        assertEquals(3, navigator.backStack.size)
        assertSame(s1, navigator.backStack[1])
        assertSame(s2, navigator.last())
    }

    @Test
    fun missFallsBackToPlainPushWithFullCallbacks() = withRouter { navigator, root ->
        registerSingleRoute(Single::class, singleTask = false) { Single() }
        val callbacks = mutableListOf<String>()
        navigator.push { destination = Plain() }
        runCurrent()

        navigator.push {
            url = SINGLE_URL
            singleTask()
            routeBeforeCallback = { callbacks += "before" }
            routeSuccessCallback = { callbacks += "success" }
            routeCompletedCallback = { callbacks += "completed" }
        }
        runCurrent()

        // 栈中无同类型实例：新实例正常入栈，回调生命周期与普通 push 一致。
        assertEquals(3, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertTrue(navigator.last() is Single)
        assertEquals(listOf("before", "success", "completed"), callbacks)
    }

    @Test
    fun alreadyTopIsIdempotentSuccessWithoutEventsOrBeforeCallback() = withRouter { navigator, root ->
        registerSingleRoute(Single::class, singleTask = false) { Single() }
        val events = mutableListOf<BackStackEvent>()
        backgroundScope.launch { navigator.backStackEvents.collect { events += it } }
        val callbacks = mutableListOf<String>()
        val s1 = Single()
        navigator.push { destination = s1 }
        runCurrent()
        events.clear()

        navigator.push {
            url = SINGLE_URL
            singleTask()
            routeBeforeCallback = { callbacks += "before" }
            routeSuccessCallback = { callbacks += "success" }
            routeFailureCallback = { callbacks += "failure" }
            routeCompletedCallback = { callbacks += "completed" }
        }
        runCurrent()

        // 目标已是栈顶：幂等成功，无栈变更、无事件、不发 before/failure。
        assertEquals(2, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertSame(s1, navigator.last())
        assertTrue(events.isEmpty())
        assertEquals(listOf("success", "completed"), callbacks)
    }

    @Test
    fun moveEmitsBeforeCallbackThenSuccessAndCompleted() = withRouter { navigator, _ ->
        val callbacks = mutableListOf<String>()
        val s1 = Single()
        navigator.push { destination = s1 }
        navigator.push { destination = Plain() }
        runCurrent()

        navigator.push {
            destination = s1
            singleTask()
            routeBeforeCallback = { callbacks += "before" }
            routeSuccessCallback = { callbacks += "success" }
            routeFailureCallback = { callbacks += "failure" }
            routeCompletedCallback = { callbacks += "completed" }
        }
        runCurrent()

        // 实际发生栈变更时走完整生命周期：before -> success -> completed。
        assertSame(s1, navigator.last())
        assertEquals(listOf("before", "success", "completed"), callbacks)
    }

    @Test
    fun annotationDefaultAppliesWhenBuilderLeavesIntentUnset() = withRouter { navigator, root ->
        registerSingleRoute(Single::class, singleTask = true) { Single() }
        val s1 = Single()
        navigator.push { destination = s1 }
        navigator.push { destination = Plain() }
        navigator.push { destination = Plain() }
        runCurrent()

        navigator.push { url = SINGLE_URL }
        runCurrent()

        // @Router(singleTask = true) 默认生效：无需 builder 意图。
        assertEquals(2, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertSame(s1, navigator.last())
    }

    @Test
    fun builderExplicitFalseOverridesAnnotationTrue() = withRouter { navigator, _ ->
        registerSingleRoute(Single::class, singleTask = true) { Single() }
        val s1 = Single()
        navigator.push { destination = s1 }
        navigator.push { destination = Plain() }
        runCurrent()

        navigator.push { url = SINGLE_URL; singleTask(false) }
        runCurrent()

        // 显式关闭覆盖注解默认：新实例正常叠加，不复用 s1。
        assertEquals(4, navigator.backStack.size)
        assertSame(s1, navigator.backStack[1])
        assertTrue(navigator.last() is Single)
        assertNotSameInstance(s1, navigator.last())
    }

    @Test
    fun interceptorReadsIntentAndCanAttachSingleTaskByRewrite() = withRouter(
        global = object : IRouterPushInterceptor {
            override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
                seenIntents += chain.request().singleTask
                return if (chain.request().singleTask == null && chain.request().uri.toString() == SINGLE_URL) {
                    chain.proceed(chain.request().newBuilder().singleTask(true).build())
                } else {
                    chain.proceed(chain.request())
                }
            }
        },
    ) { navigator, _ ->
        registerSingleRoute(Single::class, singleTask = false) { Single() }
        val s1 = Single()
        navigator.push { destination = s1 }
        navigator.push { destination = Plain() }
        runCurrent()
        seenIntents.clear()

        // 第一次：无意图（null）被改写为启用，已有实例拉顶。
        navigator.push { url = SINGLE_URL }
        runCurrent()
        assertSame(s1, navigator.last())

        // 第二次：显式 true 原样透传。
        navigator.push { destination = Plain(); singleTask() }
        runCurrent()
        assertEquals(listOf<Boolean?>(null, true), seenIntents)
    }

    @Test
    fun redirectResolvesAnnotationDefaultOfFinalTarget() = withRouter(
        global = object : IRouterPushInterceptor {
            override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
                return if (chain.request().uri.toString() == PLAIN_URL) {
                    chain.proceed(chain.request().newBuilder().url(GATE_URL).build())
                } else {
                    chain.proceed(chain.request())
                }
            }
        },
    ) { navigator, root ->
        registerSingleRoute(Plain::class, url = PLAIN_URL, singleTask = false) { Plain() }
        registerSingleRoute(Gate::class, url = GATE_URL, singleTask = true) { Gate() }
        val g1 = Gate()
        navigator.push { destination = g1 }
        navigator.push { destination = Plain() }
        runCurrent()

        // 原 target Plain 注解为 false，redirect 后按最终目标 Gate 的注解默认（true）解析并复用 g1。
        navigator.push { url = PLAIN_URL }
        runCurrent()

        assertEquals(2, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertSame(g1, navigator.last())
    }

    @Test
    fun greenChannelMatchesInterceptorPath() = withRouter { navigator, root ->
        registerSingleRoute(Single::class, singleTask = false) { Single() }
        val s1 = Single()
        navigator.push { destination = s1 }
        navigator.push { destination = Plain() }
        runCurrent()

        navigator.push { url = SINGLE_URL; singleTask(); greenChannel = true }
        runCurrent()

        assertEquals(2, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertSame(s1, navigator.last())
    }

    @Test
    fun conflictingConfigsAreRejectedIncludingAnnotationEffectiveValue() = withRouter { navigator, _ ->
        registerSingleRoute(Single::class, singleTask = true) { Single() }
        navigator.push { destination = Plain() }
        runCurrent()
        val before = navigator.backStack.toList()
        var failures = 0
        var completed = 0

        navigator.push {
            url = SINGLE_URL; singleTask(); popUpTo<Root>()
            routeFailureCallback = { failures++ }; routeCompletedCallback = { completed++ }
        }
        runCurrent()
        navigator.push {
            url = SINGLE_URL; singleTask(); isCleanStack = true
            routeFailureCallback = { failures++ }; routeCompletedCallback = { completed++ }
        }
        runCurrent()
        navigator.push {
            url = SINGLE_URL; singleTask(); isPopCurrent = true
            routeFailureCallback = { failures++ }; routeCompletedCallback = { completed++ }
        }
        runCurrent()
        navigator.push {
            url = SINGLE_URL; singleTask(); onRouteResult<Int> { }
            routeFailureCallback = { failures++ }; routeCompletedCallback = { completed++ }
        }
        runCurrent()
        navigator.push {
            url = SINGLE_URL; singleTask(); preventDuplicates = true
            routeFailureCallback = { failures++ }; routeCompletedCallback = { completed++ }
        }
        runCurrent()
        // 注解默认生效值同样触发拒绝：singleTask=true + 结果绑定。
        navigator.push {
            url = SINGLE_URL; onRouteResult<Int> { }
            routeFailureCallback = { failures++ }; routeCompletedCallback = { completed++ }
        }
        runCurrent()

        assertEquals(6, failures)
        assertEquals(6, completed)
        assertEquals(before, navigator.backStack.toList())
    }

    @Test
    fun protectedPageAboveTargetIsKeptWhileTargetMovesToActualTop() =
        withRouter(protected = Protected::class) { navigator, root ->
            val events = mutableListOf<BackStackEvent>()
            backgroundScope.launch { navigator.backStackEvents.collect { events += it } }
            registerSingleRoute(Single::class, singleTask = false) { Single() }
            val s1 = Single()
            navigator.push { destination = s1 }
            navigator.push { destination = Protected() }
            navigator.push { destination = Plain() }
            runCurrent()
            events.clear()

            navigator.push { url = SINGLE_URL; singleTask() }
            runCurrent()

            // 受保护页面不被移除但位于目标之下；目标移动到实际栈顶。
            assertEquals(3, navigator.backStack.size)
            assertSame(root, navigator.backStack[0])
            assertTrue(navigator.backStack[1] is Protected)
            assertSame(s1, navigator.last())
            assertEquals(listOf("Pop", "Push"), events.map { it::class.simpleName })
            assertSame(s1, (events.last() as BackStackEvent.Push).entry)
        }

    @Test
    fun targetBelowOnlyProtectedPagesStillMovesToActualTop() =
        withRouter(protected = Protected::class) { navigator, root ->
            val events = mutableListOf<BackStackEvent>()
            backgroundScope.launch { navigator.backStackEvents.collect { events += it } }
            val callbacks = mutableListOf<String>()
            registerSingleRoute(Single::class, singleTask = false) { Single() }
            val s1 = Single()
            navigator.push { destination = s1 }
            navigator.push { destination = Protected() }
            runCurrent()
            events.clear()

            navigator.push {
                url = SINGLE_URL
                singleTask()
                routeBeforeCallback = { callbacks += "before" }
                routeSuccessCallback = { callbacks += "success" }
                routeCompletedCallback = { callbacks += "completed" }
            }
            runCurrent()

            // 上方无可移除页面时仍要移动目标到实际栈顶，只发一条 Push 事件。
            assertEquals(3, navigator.backStack.size)
            assertSame(root, navigator.backStack[0])
            assertTrue(navigator.backStack[1] is Protected)
            assertSame(s1, navigator.last())
            assertEquals(listOf("Push"), events.map { it::class.simpleName })
            assertEquals(listOf("before", "success", "completed"), callbacks)
        }

    @Test
    fun protectedHoverScenarioDeliversCancelledOnlyAfterTargetIsActualTop() =
        withRouter(protected = Protected::class) { navigator, root ->
            registerSingleRoute(Single::class, singleTask = false) { Single() }
            val results = mutableListOf<RouteResult<Int>>()
            val s1 = Single()
            navigator.push { destination = s1 }
            runCurrent()
            // s1 等待 B 的结果；B 之上压一个受保护页和普通页。
            navigator.push { destination = Plain(); onRouteResult<Int> { results += it } }
            runCurrent()
            navigator.push { destination = Protected() }
            navigator.push { destination = Plain() }
            runCurrent()

            navigator.push { url = SINGLE_URL; singleTask() }
            runCurrent()

            // B 按 Cancelled 结算入队；目标成为实际栈顶后才消费 inbox，立即收到结果。
            assertEquals(3, navigator.backStack.size)
            assertSame(root, navigator.backStack[0])
            assertTrue(navigator.backStack[1] is Protected)
            assertSame(s1, navigator.last())
            assertEquals<List<RouteResult<Int>>>(listOf(RouteResult.Cancelled), results)
        }

    @Test
    fun protectedTargetItselfIsStillReused() =
        withRouter(protected = Single::class) { navigator, root ->
            registerSingleRoute(Single::class, singleTask = false) { Single() }
            val s1 = Single()
            navigator.push { destination = s1 }
            navigator.push { destination = Plain() }
            navigator.push { destination = Plain() }
            runCurrent()

            navigator.push { url = SINGLE_URL; singleTask() }
            runCurrent()

            // 点名目标不受被动移除豁免限制：目标自身可被复用拉顶，其上方普通页面照常移除。
            assertEquals(2, navigator.backStack.size)
            assertSame(root, navigator.backStack[0])
            assertSame(s1, navigator.last())
        }

    @Test
    fun trimmedWaitingCallerReceivesCancelledImmediatelyWhenTargetIsBackOnTop() =
        withRouter { navigator, root ->
            registerSingleRoute(Single::class, singleTask = false) { Single() }
            val results = mutableListOf<RouteResult<Int>>()
            val s1 = Single()
            navigator.push { destination = s1 }
            runCurrent()
            navigator.push { destination = Plain(); onRouteResult<Int> { results += it } }
            runCurrent()
            navigator.push { destination = Plain() }
            runCurrent()

            navigator.push { url = SINGLE_URL; singleTask() }
            runCurrent()

            // 等待页被移除按 Cancelled 结算；目标回到最顶后立即消费 inbox。
            assertSame(s1, navigator.last())
            assertEquals<List<RouteResult<Int>>>(listOf(RouteResult.Cancelled), results)
        }

    private val seenIntents = mutableListOf<Boolean?>()

    private fun assertNotSameInstance(expected: Any?, actual: Any?) {
        assertTrue(expected !== actual)
    }

    private fun registerSingleRoute(
        clazz: KClass<*>,
        url: String = SINGLE_URL,
        singleTask: Boolean,
        provider: (Map<String, Any>?) -> IRouterEntryProvider,
    ) {
        val host = url.substringAfter("://").substringBefore("/")
        val path = url.substringAfter("/", "")
        Core.aliasesKV[url] = clazz
        Core.masterKV[clazz] = ParseRouterInfo(
            url = url,
            scheme = "test",
            host = host,
            path = path,
            desc = "",
            pushInterceptors = emptyList(),
            popInterceptors = emptyList(),
            instanceProvider = object : IRouterInstance {
                override fun instance(extras: Map<String, Any>?): IRouterEntryProvider = provider(extras)
            },
            singleTask = singleTask,
        )
    }

    private fun withRouter(
        global: IRouterPushInterceptor? = null,
        protected: KClass<*>? = null,
        block: suspend TestScope.(Navigator, Root) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val providers = RouterSingleton.providers.toList()
        RouterSingleton.providers.clear()
        RouterSingleton.providers += object : ModuleRouter {
            override fun routeMasterKV(): Map<KClass<*>, ParseRouterInfo> = emptyMap()
            override fun routeAliasesKV(): Map<String, KClass<*>> = emptyMap()
            override fun globalPushInterceptors(): List<GlobalPushInterceptorInfo> =
                global?.let { listOf(GlobalPushInterceptorInfo(it, 0)) }.orEmpty()
            override fun globalPopInterceptors(): List<GlobalPopInterceptorInfo> = emptyList()
            override fun entryProviderInstaller(): EntryProviderInstaller = {}
        }
        val root = Root()
        val navigator = if (protected != null) Navigator(root) { protect(protected) } else Navigator(root)
        val savedMasterKeys = Core.masterKV.keys.toSet()
        val savedAliasKeys = Core.aliasesKV.keys.toSet()
        try {
            runCurrent()
            block(navigator, root)
        } finally {
            navigator.core.cancel()
            // 清理本用例注册的测试路由，避免污染全局注册表。
            Core.masterKV.keys.removeAll { it !in savedMasterKeys }
            Core.aliasesKV.keys.removeAll { it !in savedAliasKeys }
            RouterSingleton.providers.clear()
            RouterSingleton.providers.addAll(providers)
            Dispatchers.resetMain()
        }
    }

    companion object {
        private const val SINGLE_URL = "test://single/task"
        private const val PLAIN_URL = "test://single/plain"
        private const val GATE_URL = "test://single/gate"
    }
}
