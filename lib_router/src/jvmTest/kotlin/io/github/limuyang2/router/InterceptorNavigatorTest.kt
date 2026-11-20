package io.github.limuyang2.router

import io.github.limuyang2.router.info.ParseRouterInfo
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Chain.navigator() 必须返回发起当前操作的 Navigator，确保多栈场景下拦截器按所属栈工作。
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
    fun runtimePushInterceptorsDeduplicateByIdentityAndRespectPriority() = withRouter { first, second ->
        val calls = mutableListOf<String>()
        val later = pushInterceptor { calls += "later" }
        val earlier = pushInterceptor { calls += "earlier" }
        val equalFirst = pushInterceptor { calls += "equalFirst" }
        val equalSecond = pushInterceptor { calls += "equalSecond" }

        assertTrue(first.addPushInterceptor(later, priority = 5))
        assertFalse(first.addPushInterceptor(later, priority = 0))
        assertTrue(first.addPushInterceptor(earlier, priority = 1))
        assertTrue(first.addPushInterceptor(equalFirst, priority = 3))
        assertTrue(first.addPushInterceptor(equalSecond, priority = 3))

        first.push { destination = PageA() }
        second.push { destination = PageB() }
        runCurrent()

        assertEquals(listOf("earlier", "equalFirst", "equalSecond", "later"), calls)
        assertTrue(first.removePushInterceptor(later))
        assertFalse(first.removePushInterceptor(later))
    }

    @Test
    fun runtimePopInterceptorsAreNavigatorLocal() = withRouter { first, second ->
        val calls = mutableListOf<Navigator>()
        val interceptor = object : IRouterPopInterceptor {
            override suspend fun intercept(chain: IRouterPopInterceptor.Chain): PopResponse {
                calls += chain.navigator()
                return chain.proceed()
            }
        }

        assertTrue(first.addPopInterceptor(interceptor))
        first.push { destination = PageA() }
        second.push { destination = PageB() }
        runCurrent()
        first.pop()
        second.pop()
        runCurrent()

        assertEquals(listOf(first), calls)
        assertTrue(first.removePopInterceptor(interceptor))
        assertFalse(first.removePopInterceptor(interceptor))
    }

    @Test
    fun changesDuringPushApplyOnlyToSubsequentOperations() = withRouter { first, _ ->
        val gate = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val original = object : IRouterPushInterceptor {
            override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
                calls += "original"
                gate.await()
                return chain.proceed(chain.request())
            }
        }
        val replacement = pushInterceptor { calls += "replacement" }

        assertTrue(first.addPushInterceptor(original))
        first.push { destination = PageA() }
        runCurrent()
        assertEquals(listOf("original"), calls)

        assertTrue(first.removePushInterceptor(original))
        assertTrue(first.addPushInterceptor(replacement))
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("original"), calls)

        calls.clear()
        first.push { destination = PageB() }
        runCurrent()
        assertEquals(listOf("replacement"), calls)
    }

    @Test
    fun taggedGlobalInterceptorsOnlyRunOnMatchingNavigators() {
        val pushCalls = mutableListOf<Navigator>()
        val popCalls = mutableListOf<Navigator>()
        withRouter(
            push = object : IRouterPushInterceptor {
                override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
                    pushCalls += chain.navigator()
                    return chain.proceed(chain.request())
                }
            },
            pushTags = setOf("main"),
            pop = object : IRouterPopInterceptor {
                override suspend fun intercept(chain: IRouterPopInterceptor.Chain): PopResponse {
                    popCalls += chain.navigator()
                    return chain.proceed()
                }
            },
            popTags = setOf("main"),
            firstTags = setOf("main"),
        ) { first, second ->
            first.push { destination = PageA() }
            second.push { destination = PageB() }
            runCurrent()
            first.pop()
            second.pop()
            runCurrent()

            assertEquals(listOf(first), pushCalls)
            assertEquals(listOf(first), popCalls)
        }
    }

    @Test
    fun globalInterceptorMatchesAnyNavigatorTag() = withRouter(
        push = object : IRouterPushInterceptor {
            override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
                seenNavigators += chain.navigator()
                return chain.proceed(chain.request())
            }
        },
        pushTags = setOf("main", "modal"),
        firstTags = setOf("modal"),
    ) { first, second ->
        first.push { destination = PageA() }
        second.push { destination = PageB() }
        runCurrent()

        assertEquals(listOf(first), seenNavigators)
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

    private fun pushInterceptor(block: () -> Unit) = object : IRouterPushInterceptor {
        override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
            block()
            return chain.proceed(chain.request())
        }
    }

    private fun withRouter(
        push: IRouterPushInterceptor? = null,
        pushTags: Set<String> = emptySet(),
        pop: IRouterPopInterceptor? = null,
        popTags: Set<String> = emptySet(),
        firstTags: Set<String> = emptySet(),
        secondTags: Set<String> = emptySet(),
        block: suspend TestScope.(Navigator, Navigator) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val providers = RouterSingleton.providers.toList()
        RouterSingleton.providers.clear()
        RouterSingleton.providers += object : ModuleRouter {
            override fun routeMasterKV(): Map<KClass<*>, ParseRouterInfo> = emptyMap()
            override fun routeAliasesKV(): Map<String, KClass<*>> = emptyMap()
            override fun globalPushInterceptors(): List<GlobalPushInterceptorInfo> =
                push?.let { listOf(GlobalPushInterceptorInfo(it, 0, pushTags)) }.orEmpty()
            override fun globalPopInterceptors(): List<GlobalPopInterceptorInfo> =
                pop?.let { listOf(GlobalPopInterceptorInfo(it, 0, popTags)) }.orEmpty()
            override fun entryProviderInstaller(): EntryProviderInstaller = {}
        }
        // 全局拦截器在 Core 构造期快照，必须先注册再构造两个 Navigator。
        val first = Navigator(Root()) { tags(*firstTags.toTypedArray()) }
        val second = Navigator(Root()) { tags(*secondTags.toTypedArray()) }
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
