package io.github.limuyang2.router

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
 * popUpTo 是「修剪到锚点再压入新页」的一步原子语义：保留锚点及其以下页面，
 * 移除锚点与新页之间的夹层；锚点缺失时退化为普通 push，组合冲突直接拒绝。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PopUpToTest {
    private class Root : IRouterEntryProvider
    private class PageA : IRouterEntryProvider
    private class PageB : IRouterEntryProvider
    private class Login : IRouterEntryProvider
    private class Missing : IRouterEntryProvider

    @Test
    fun trimsMiddleLayersAboveAnchorAndEmitsPopEventsBeforePush() = withRouter { navigator, root ->
        val events = mutableListOf<BackStackEvent>()
        backgroundScope.launch { navigator.backStackEvents.collect { events += it } }
        val a = PageA(); val b = PageB()
        navigator.push { destination = a }; navigator.push { destination = b }
        runCurrent()
        events.clear()

        val c = Login()
        navigator.push { destination = c; popUpTo<PageA>() }
        runCurrent()

        // 锚点及其以下保留，锚点与新页之间的夹层被移除。
        assertEquals(3, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertSame(a, navigator.backStack[1])
        assertSame(c, navigator.backStack[2])
        // 夹层按移除序派发 Pop 事件，随后才是新页的 Push 事件。
        val classes = events.map { it::class.simpleName }
        assertEquals(listOf("Pop", "Push"), classes)
    }

    @Test
    fun anchorAtRootAllowsPopBackToRootAfterGatePush() = withRouter { navigator, root ->
        val a = PageA(); val b = PageB()
        navigator.push { destination = a }; navigator.push { destination = b }
        runCurrent()

        val login = Login()
        navigator.push { destination = login; popUpTo(root) }
        runCurrent()

        assertEquals(2, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertSame(login, navigator.backStack[1])

        // 登录成功后的 pop 不撞根页保护，回到锚点。
        navigator.pop()
        runCurrent()
        assertEquals(1, navigator.backStack.size)
        assertSame(root, navigator.last())
    }

    @Test
    fun trimmedWaitingCallerSettlesCancelledAfterReturningToForeground() = withRouter { navigator, root ->
        val results = mutableListOf<RouteResult<Int>>()
        val a = PageA()
        navigator.push { destination = a }
        runCurrent()
        navigator.push { destination = PageB(); onRouteResult<Int> { results += it } }
        runCurrent()

        navigator.push { destination = Login(); popUpTo<PageA>() }
        runCurrent()

        // 被剪的等待页按 Cancelled 结算；caller 保留在锚点上，回到前台后消费。
        assertEquals(3, navigator.backStack.size)
        assertTrue(results.isEmpty())
        navigator.pop()
        runCurrent()
        assertSame(a, navigator.last())
        assertEquals<List<RouteResult<Int>>>(listOf(RouteResult.Cancelled), results)
    }

    @Test
    fun missingAnchorSkipsTrimmingAndKeepsPlainPush() = withRouter { navigator, root ->
        val a = PageA()
        navigator.push { destination = a }
        runCurrent()

        val c = Login()
        navigator.push { destination = c; popUpTo<Missing>() }
        runCurrent()

        assertEquals(3, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertSame(a, navigator.backStack[1])
        assertSame(c, navigator.backStack[2])
    }

    @Test
    fun conflictingFlagsAreRejectedBeforeStackChanges() = withRouter { navigator, _ ->
        val a = PageA()
        navigator.push { destination = a }
        runCurrent()
        val before = navigator.backStack.toList()
        var failures = 0
        var completed = 0

        navigator.push {
            destination = Login(); popUpTo<Root>(); isCleanStack = true
            routeFailureCallback = { failures++ }; routeCompletedCallback = { completed++ }
        }
        runCurrent()
        navigator.push {
            destination = Login(); popUpTo<Root>(); isPopCurrent = true
            routeFailureCallback = { failures++ }; routeCompletedCallback = { completed++ }
        }
        runCurrent()
        navigator.push {
            destination = Login(); popUpTo<Root>(); onRouteResult<Int> { }
            routeFailureCallback = { failures++ }; routeCompletedCallback = { completed++ }
        }
        runCurrent()

        assertEquals(3, failures)
        assertEquals(3, completed)
        assertEquals(before, navigator.backStack.toList())
    }

    @Test
    fun interceptorRewriteCanAttachPopUpToForLoginGate() = withRouter(
        global = object : IRouterPushInterceptor {
            override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
                val rewritten = chain.request().newBuilder()
                    .destination(Login())
                    .popUpTo(Root::class)
                    .build()
                return chain.proceed(rewritten)
            }
        },
    ) { navigator, root ->
        navigator.push { destination = PageB() }
        navigator.push { destination = PageA() }
        runCurrent()

        assertEquals(2, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertTrue(navigator.backStack[1] is Login)
    }

    @Test
    fun greenChannelHonorsPopUpTo() = withRouter { navigator, root ->
        val a = PageA()
        navigator.push { destination = a }
        runCurrent()

        navigator.push {
            destination = Login()
            popUpTo<Root>()
            greenChannel = true
        }
        runCurrent()

        assertEquals(2, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertTrue(navigator.backStack[1] is Login)
    }

    private fun withRouter(
        global: IRouterPushInterceptor? = null,
        block: suspend TestScope.(Navigator, Root) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val providers = RouterSingleton.providers.toList()
        RouterSingleton.providers.clear()
        RouterSingleton.providers += object : ModuleRouter {
            override fun routeMasterKV(): Map<KClass<*>, io.github.limuyang2.router.info.ParseRouterInfo> = emptyMap()
            override fun routeAliasesKV(): Map<String, KClass<*>> = emptyMap()
            override fun globalPushInterceptors(): List<GlobalPushInterceptorInfo> =
                global?.let { listOf(GlobalPushInterceptorInfo(it, 0)) }.orEmpty()
            override fun globalPopInterceptors(): List<GlobalPopInterceptorInfo> = emptyList()
            override fun entryProviderInstaller(): EntryProviderInstaller = {}
        }
        val root = Root()
        val navigator = Navigator(root)
        try {
            runCurrent()
            block(navigator, root)
        } finally {
            navigator.core.cancel()
            RouterSingleton.providers.clear()
            RouterSingleton.providers.addAll(providers)
            Dispatchers.resetMain()
        }
    }
}
