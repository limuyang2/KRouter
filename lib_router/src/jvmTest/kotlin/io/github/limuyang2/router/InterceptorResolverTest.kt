package io.github.limuyang2.router

import io.github.limuyang2.router.info.ParseRouterInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * interceptorResolver 的解析顺序契约：resolver 命中优先采用，未命中或未注册走无参构造兜底；
 * DI-only 类（无兜底）未命中必须快速失败；initRouter 首次配置生效。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InterceptorResolverTest {
    private class Root : IRouterEntryProvider
    private class PageA : IRouterEntryProvider

    private class Repo

    /** 模拟 DI 构造注入的 Push 拦截器：没有无参构造，只能由 resolver 提供。 */
    private class DiPush(val repo: Repo) : IRouterPushInterceptor {
        var hits = 0
        override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
            hits++
            return chain.proceed(chain.request())
        }
    }

    /** 模拟 DI 构造注入的 Pop 拦截器。 */
    private class DiPop(val repo: Repo) : IRouterPopInterceptor {
        var hits = 0
        override suspend fun intercept(chain: IRouterPopInterceptor.Chain): PopResponse {
            hits++
            return chain.proceed()
        }
    }

    /** 有无参构造的拦截器，resolver 未命中时走生成代码兜底。 */
    private class NoArgPush : IRouterPushInterceptor {
        override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse =
            chain.proceed(chain.request())
    }

    @Test
    fun resolverHitProvidesInstanceWithoutFallback() = withRegistry { registry ->
        val provided = NoArgPush()
        registry[NoArgPush::class] = provided
        assertSame(provided, resolvePushInterceptor(NoArgPush::class) {
            throw AssertionError("resolver 命中时不得执行无参构造兜底")
        })
    }

    @Test
    fun resolverMissFallsBackToNoArgConstruction() = withRegistry { registry ->
        val fromFallback = NoArgPush()
        var fallbackInvoked = false
        val resolved = resolvePushInterceptor(NoArgPush::class) {
            fallbackInvoked = true
            fromFallback
        }
        assertSame(fromFallback, resolved)
        assertTrue(fallbackInvoked)
    }

    @Test
    fun absentResolverFallsBackToNoArgConstruction() = withoutResolver {
        val fromFallback = NoArgPush()
        assertSame(fromFallback, resolvePushInterceptor(NoArgPush::class) { fromFallback })
    }

    @Test
    fun resolverReturningWrongTypeFailsFast() = withRegistry { registry ->
        registry[NoArgPush::class] = "not an interceptor"
        val error = assertFailsWith<IllegalStateException> {
            resolvePushInterceptor(NoArgPush::class) { NoArgPush() }
        }
        assertTrue(error.message!!.contains("IRouterPushInterceptor"))
    }

    @Test
    fun resolverOnlyClassRequiresResolver() {
        withoutResolver {
            val error = assertFailsWith<IllegalStateException> {
                resolvePushInterceptor(DiPush::class)
            }
            assertTrue(error.message!!.contains("interceptorResolver"))
        }
        withRegistry {
            val error = assertFailsWith<IllegalStateException> {
                resolvePopInterceptor(DiPop::class)
            }
            assertTrue(error.message!!.contains("interceptorResolver"))
        }
    }

    @Test
    fun resolverOnlyClassUsesResolverInstance() = withRegistry { registry ->
        val provided = DiPush(Repo())
        registry[DiPush::class] = provided
        assertSame(provided, resolvePushInterceptor(DiPush::class))
        assertEquals(0, provided.hits)
    }

    @Test
    fun firstConfigurationWins() = withRegistry { registry ->
        val first = DiPush(Repo())
        registry[DiPush::class] = first
        // 首次配置已生效；第二次 initRouter 换成会返回错误类型的 resolver 也不得替换。
        initRouter { interceptorResolver { DiPop(Repo()) } }
        assertSame(first, resolvePushInterceptor(DiPush::class))
    }

    /**
     * 端到端：手写 ModuleRouter 以生成代码的形态调用 resolveXxxInterceptor，
     * Navigator 构造期快照到 resolver 提供的实例，push/pop 实际执行它。
     */
    @Test
    fun generatedShapedResolutionRunsInRealChain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val providers = RouterSingleton.providers.toList()
        RouterSingleton.providers.clear()

        val diPush = DiPush(Repo())
        val diPop = DiPop(Repo())
        initRouter {
            interceptorResolver { clazz ->
                when (clazz) {
                    DiPush::class -> diPush
                    DiPop::class -> diPop
                    else -> null
                }
            }
        }
        RouterSingleton.providers += object : ModuleRouter {
            override fun routeMasterKV(): Map<KClass<*>, ParseRouterInfo> = emptyMap()
            override fun routeAliasesKV(): Map<String, KClass<*>> = emptyMap()
            override fun globalPushInterceptors(): List<GlobalPushInterceptorInfo> =
                listOf(GlobalPushInterceptorInfo(resolvePushInterceptor(DiPush::class), 0))
            override fun globalPopInterceptors(): List<GlobalPopInterceptorInfo> =
                listOf(GlobalPopInterceptorInfo(resolvePopInterceptor(DiPop::class), 0))
            override fun entryProviderInstaller(): EntryProviderInstaller = {}
        }
        val navigator = Navigator(Root())
        try {
            navigator.push { destination = PageA() }
            runCurrent()
            assertEquals(1, diPush.hits)

            navigator.pop()
            runCurrent()
            assertEquals(1, diPop.hits)
            assertEquals(1, navigator.backStack.size)
        } finally {
            navigator.core.cancel()
            RouterSingleton.resetForTest()
            RouterSingleton.providers.addAll(providers)
            Dispatchers.resetMain()
        }
    }

    /**
     * 以可变注册表初始化 resolver（命中读表，未命中返回 null），结束后恢复未初始化现场。
     */
    private fun withRegistry(block: (MutableMap<KClass<*>, Any?>) -> Unit) {
        val providers = RouterSingleton.providers.toList()
        RouterSingleton.providers.clear()
        val registry = mutableMapOf<KClass<*>, Any?>()
        try {
            initRouter { interceptorResolver { clazz -> registry[clazz] } }
            block(registry)
        } finally {
            RouterSingleton.resetForTest()
            RouterSingleton.providers.addAll(providers)
        }
    }

    /** 不注册 resolver 初始化，结束后恢复未初始化现场。 */
    private fun withoutResolver(block: () -> Unit) {
        val providers = RouterSingleton.providers.toList()
        RouterSingleton.providers.clear()
        try {
            initRouter { }
            block()
        } finally {
            RouterSingleton.resetForTest()
            RouterSingleton.providers.addAll(providers)
        }
    }
}
