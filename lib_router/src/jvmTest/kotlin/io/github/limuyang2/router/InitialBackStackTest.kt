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
import kotlin.test.assertTrue

/**
 * 初始路由必须在 Core 构造期间同步入栈：任何需要泵调度器才能看到首页的实现，
 * 都会让首帧组合读到占位/空栈，表现为启动闪一下空白页。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InitialBackStackTest {
    private data class Page(val id: Int = 0) : IRouterEntryProvider
    private object Login : IRouterEntryProvider

    @Test
    fun instanceStartDestinationIsSeededWithoutPumpingScheduler() = withRouter {
        val start = Page(7)
        val navigator = Navigator(start)
        try {
            assertEquals(1, navigator.backStack.size)
            assertSame(start, navigator.last())
        } finally {
            navigator.core.cancel()
        }
    }

    @Test
    fun urlStartDestinationResolvesQueryParamsWithoutPumpingScheduler() = withRouter {
        Core.aliasesKV["test://start/home"] = Page::class
        var receivedExtras: Map<String, Any>? = null
        val start = Page(42)
        Core.masterKV[Page::class] = ParseRouterInfo(
            url = "test://start/home",
            scheme = "test",
            host = "start",
            path = "home",
            desc = "",
            pushInterceptors = emptyList(),
            popInterceptors = emptyList(),
            instanceProvider = object : IRouterInstance {
                override fun instance(extras: Map<String, Any>?): IRouterEntryProvider {
                    receivedExtras = extras
                    return start
                }
            },
        )

        val navigator = Navigator("test://start/home?id=42")
        try {
            assertEquals(1, navigator.backStack.size)
            assertSame(start, navigator.last())
            assertEquals(mapOf("id" to listOf("42")), receivedExtras)
        } finally {
            navigator.core.cancel()
        }
    }

    @Test
    fun unregisteredUrlSeedsVisibleUnknownRoute() = withRouter {
        val navigator = Navigator("test://missing/page")
        try {
            assertEquals(1, navigator.backStack.size)
            val top = navigator.last()
            assertTrue(top is UnknownRoute)
            assertEquals("test://missing/page", top.uri.toString())
        } finally {
            navigator.core.cancel()
        }
    }

    @Test
    fun startDestinationSkipsGlobalPushInterceptors() = withRouter {
        var intercepted = 0
        RouterSingleton.providers += object : ModuleRouter {
            override fun routeMasterKV(): Map<KClass<*>, ParseRouterInfo> = emptyMap()
            override fun routeAliasesKV(): Map<String, KClass<*>> = emptyMap()
            override fun globalPushInterceptors(): List<GlobalPushInterceptorInfo> =
                listOf(
                    GlobalPushInterceptorInfo(
                        interceptor = object : IRouterPushInterceptor {
                            override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
                                intercepted++
                                return chain.interrupt()
                            }
                        },
                        priority = 0,
                    )
                )

            override fun globalPopInterceptors(): List<GlobalPopInterceptorInfo> = emptyList()
            override fun entryProviderInstaller(): EntryProviderInstaller = {}
        }

        val start = Page(1)
        val navigator = Navigator(start)
        try {
            // 初始路由不经过拦截器链，首页直接就位。
            assertSame(start, navigator.last())
            assertEquals(0, intercepted)

            // 后续普通 push 仍会被全局拦截器中断。
            navigator.push { destination = Login }
            runCurrent()
            assertEquals(1, intercepted)
            assertSame(start, navigator.last())
            assertEquals(1, navigator.backStack.size)
        } finally {
            navigator.core.cancel()
        }
    }

    private fun withRouter(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val providers = RouterSingleton.providers.toList()
        val routes = Core.masterKV.toMap()
        val aliases = Core.aliasesKV.toMap()
        RouterSingleton.providers.clear()
        Core.masterKV.clear()
        Core.aliasesKV.clear()
        try {
            block()
        } finally {
            RouterSingleton.providers.clear()
            RouterSingleton.providers.addAll(providers)
            Core.masterKV.clear()
            Core.masterKV.putAll(routes)
            Core.aliasesKV.clear()
            Core.aliasesKV.putAll(aliases)
            Dispatchers.resetMain()
        }
    }
}
