package io.github.limuyang2.router

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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

/**
 * 受保护页面（柱模型）：无论在栈内哪个位置都不被移除操作带走——
 * 修剪穿透继续，仅其自身豁免保留原位；栈顶受保护时 pop 无效。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProtectedPageTest {
    private class Root : IRouterEntryProvider
    private class Home : IRouterEntryProvider
    private class PageA : IRouterEntryProvider
    private class PageB : IRouterEntryProvider
    private class Mid : IRouterEntryProvider

    @Test
    fun popWithProtectedTopDoesNothingAndNormalPopsStillWork() = withRouter(Home::class) { navigator, root ->
        val home = Home()
        navigator.push { destination = home }
        runCurrent()
        navigator.push { destination = PageA() }
        runCurrent()
        // [root, home, a]：普通 pop 删非保护栈顶。
        navigator.pop()
        runCurrent()
        assertEquals(2, navigator.backStack.size)

        // 栈顶是受保护页时 pop 无效。
        navigator.pop()
        runCurrent()
        assertEquals(2, navigator.backStack.size)
        assertSame(home, navigator.last())
    }

    @Test
    fun removeAllExcludesProtectedAndRootPages() = withRouter(Home::class) { navigator, root ->
        val home = Home()
        navigator.push { destination = home }
        navigator.push { destination = PageA() }
        runCurrent()
        // [root, home(protected), a]

        navigator.pop { all(Home::class, Root::class, PageA::class) }
        runCurrent()

        // 受保护页与根页豁免，仅删除 PageA。
        assertEquals(2, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertSame(home, navigator.backStack[1])
    }

    @Test
    fun backToTrimsThroughProtectedPageKeepingItInPlace() = withRouter(Home::class) { navigator, root ->
        val home = Home()
        val x = Mid()
        val m = PageA()
        navigator.push { destination = x }
        navigator.push { destination = home }
        navigator.push { destination = m }
        navigator.push { destination = PageB() }
        runCurrent()
        // [root, x, m, home(protected), b]

        navigator.pop { to<Mid>() }
        runCurrent()

        // 柱模型：修剪穿过 home 继续删 m、b，仅 home 豁免留在原位。
        assertEquals(3, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
        assertSame(x, navigator.backStack[1])
        assertSame(home, navigator.backStack[2])
    }

    @Test
    fun cleanStackKeepsProtectedPagesBelowNewTop() = withRouter(Home::class) { navigator, root ->
        val home = Home()
        navigator.push { destination = home }
        navigator.push { destination = PageA() }
        runCurrent()

        val b = PageB()
        navigator.push { destination = b; isCleanStack = true }
        runCurrent()

        assertEquals(2, navigator.backStack.size)
        assertSame(home, navigator.backStack[0])
        assertSame(b, navigator.backStack[1])
    }

    @Test
    fun popUpToAsRootClearsDownToProtectedPage() = withRouter(Home::class) { navigator, root ->
        val home = Home()
        val mid = Mid()
        navigator.push { destination = home }
        navigator.push { destination = mid }
        navigator.push { destination = PageA() }
        runCurrent()
        // [root, home(protected), mid, a]

        val b = PageB()
        navigator.push { destination = b; popUpTo<Mid>(asRoot = true) }
        runCurrent()

        // asRoot 清到受保护页为止：home 留下并占据栈底，mid 保留为锚点。
        assertEquals(3, navigator.backStack.size)
        assertSame(home, navigator.backStack[0])
        assertSame(mid, navigator.backStack[1])
        assertSame(b, navigator.backStack[2])
    }

    @Test
    fun runtimeProtectAndUnprotectTakeEffectImmediately() = withRouter { navigator, root ->
        val a = PageA()
        navigator.push { destination = a }
        runCurrent()
        // [root, a]：a 未受保护，pop 正常。
        navigator.pop()
        runCurrent()
        assertEquals(1, navigator.backStack.size)

        navigator.protect<PageA>()
        assertEquals(setOf<KClass<*>>(PageA::class), navigator.protectedPages)
        navigator.push { destination = PageA() }
        runCurrent()
        navigator.pop()
        runCurrent()
        assertEquals(2, navigator.backStack.size)

        navigator.unprotect<PageA>()
        navigator.pop()
        runCurrent()
        assertEquals(1, navigator.backStack.size)
    }

    @Test
    fun isPopCurrentOntoProtectedPageDegradesToPlainStacking() = withRouter { navigator, root ->
        // 根页即受保护场景：previousTop 受保护时内联 pop 无效，新页叠加。
        navigator.protect(Root::class)
        navigator.push { destination = PageA(); isPopCurrent = true }
        runCurrent()

        assertEquals(2, navigator.backStack.size)
        assertSame(root, navigator.backStack[0])
    }

    private fun withRouter(
        vararg protected: KClass<*>,
        block: suspend TestScope.(Navigator, Root) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val providers = RouterSingleton.providers.toList()
        RouterSingleton.providers.clear()
        RouterSingleton.providers += object : ModuleRouter {
            override fun routeMasterKV(): Map<KClass<*>, io.github.limuyang2.router.info.ParseRouterInfo> = emptyMap()
            override fun routeAliasesKV(): Map<String, KClass<*>> = emptyMap()
            override fun globalPushInterceptors(): List<GlobalPushInterceptorInfo> = emptyList()
            override fun globalPopInterceptors(): List<GlobalPopInterceptorInfo> = emptyList()
            override fun entryProviderInstaller(): EntryProviderInstaller = {}
        }
        val root = Root()
        val navigator = Navigator(root) { protect(protected.toSet()) }
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
