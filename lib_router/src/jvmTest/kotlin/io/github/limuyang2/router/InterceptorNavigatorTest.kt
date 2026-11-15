package io.github.limuyang2.router

import io.github.limuyang2.router.info.ParseRouterInfo
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
 * 全局拦截器是进程级单例、被所有 Navigator 共享；Chain.navigator() 必须把
 * 当前操作归属到发起它的那一个 Navigator，多栈场景下互不串扰。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InterceptorNavigatorTest {
    private class Root : IRouterEntryProvider
    private class PageA : IRouterEntryProvider
    private class PageB : IRouterEntryProvider

    @Test
    fun sharedPushInterceptorSeesOwningNavigatorPerNavigator() = withRouter(
        push = object : IRouterPushInterceptor {
            override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
                seenNavigators += chain.navigator()
                seenTops += chain.navigator().last()
                return chain.proceed(chain.request())
            }
        },
    ) { first, second ->
        val pageA = PageA()
        val pageB = PageB()

        first.push { destination = pageA }
        runCurrent()
        second.push { destination = pageB }
        runCurrent()

        assertEquals(listOf<Navigator>(first, second), seenNavigators)
        // 拦截器执行时目标尚未入栈，各自读到的是自己栈的种子页。
        assertEquals<List<IRouterEntryProvider?>>(
            listOf(first.backStack.first(), second.backStack.first()),
            seenTops,
        )
        assertSame(pageA, first.last())
        assertSame(pageB, second.last())
    }

    @Test
    fun sharedPopInterceptorSeesOwningNavigatorPerNavigator() = withRouter(
        pop = object : IRouterPopInterceptor {
            override suspend fun intercept(chain: IRouterPopInterceptor.Chain): PopResponse {
                seenNavigators += chain.navigator()
                return chain.proceed()
            }
        },
    ) { first, second ->
        first.push { destination = PageA() }
        second.push { destination = PageB() }
        runCurrent()

        first.pop()
        runCurrent()
        second.pop()
        runCurrent()

        assertEquals(listOf<Navigator>(first, second), seenNavigators)
        assertEquals(1, first.backStack.size)
        assertEquals(1, second.backStack.size)
    }

    @Test
    fun pushChainRestartKeepsOwningNavigatorBound() = withRouter(
        push = object : IRouterPushInterceptor {
            override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
                seenNavigators += chain.navigator()
                // 改写目标类型触发整条链重跑，重跑阶段仍须绑定同一个 Navigator。
                return if (chain.request().destination is PageA) {
                    chain.proceed(chain.request().newBuilder().destination(PageB()).build())
                } else {
                    chain.proceed(chain.request())
                }
            }
        },
    ) { first, _ ->
        first.push { destination = PageA() }
        runCurrent()

        assertEquals(listOf<Navigator>(first, first), seenNavigators)
        assertEquals(2, first.backStack.size)
        assertEquals(PageB::class, first.last()!!::class)
    }

    private val seenNavigators = mutableListOf<Navigator>()
    private val seenTops = mutableListOf<IRouterEntryProvider?>()

    private fun withRouter(
        push: IRouterPushInterceptor? = null,
        pop: IRouterPopInterceptor? = null,
        block: suspend TestScope.(Navigator, Navigator) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val providers = RouterSingleton.providers.toList()
        RouterSingleton.providers.clear()
        RouterSingleton.providers += object : ModuleRouter {
            override fun routeMasterKV(): Map<KClass<*>, ParseRouterInfo> = emptyMap()
            override fun routeAliasesKV(): Map<String, KClass<*>> = emptyMap()
            override fun globalPushInterceptors(): List<GlobalPushInterceptorInfo> =
                push?.let { listOf(GlobalPushInterceptorInfo(it, 0)) }.orEmpty()
            override fun globalPopInterceptors(): List<GlobalPopInterceptorInfo> =
                pop?.let { listOf(GlobalPopInterceptorInfo(it, 0)) }.orEmpty()
            override fun entryProviderInstaller(): EntryProviderInstaller = {}
        }
        // 全局拦截器在 Core 构造期快照，必须先注册再构造两个 Navigator。
        val first = Navigator(Root())
        val second = Navigator(Root())
        try {
            runCurrent()
            block(first, second)
        } finally {
            first.core.cancel()
            second.core.cancel()
            RouterSingleton.providers.clear()
            RouterSingleton.providers.addAll(providers)
            Dispatchers.resetMain()
        }
    }
}
