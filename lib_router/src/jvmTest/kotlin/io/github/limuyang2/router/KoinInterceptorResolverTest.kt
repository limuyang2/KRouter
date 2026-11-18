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
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 用真实 Koin 容器验证 interceptorResolver 的 Koin 接法（README 示例的落地形态）：
 * koinApplication 独立容器（不经全局 startKoin），DI-only 拦截器由 Koin 构造注入，
 * 生成代码形态的 resolvePushInterceptor 经 resolver 解析实例并进入真实拦截链。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KoinInterceptorResolverTest {
    private class Root : IRouterEntryProvider
    private class PageA : IRouterEntryProvider

    private class Session(val label: String = "koin-session")

    /** 构造注入 Session 的 DI-only 拦截器：无无参构造，只能由 Koin resolver 提供。 */
    private class KoinDiPush(val session: Session) : IRouterPushInterceptor {
        var hits = 0
        override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
            hits++
            return chain.proceed(chain.request())
        }
    }

    @Test
    fun koinResolverSuppliesConstructorInjectedInterceptor() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val providers = RouterSingleton.providers.toList()
        RouterSingleton.providers.clear()

        val koin = koinApplication {
            modules(
                module {
                    single { Session() }
                    single { KoinDiPush(get()) }
                }
            )
        }.koin
        initRouter {
            interceptorResolver { clazz -> koin.getOrNull<Any>(clazz) }
        }
        val provided = koin.get<KoinDiPush>()

        RouterSingleton.providers += object : ModuleRouter {
            override fun routeMasterKV(): Map<KClass<*>, ParseRouterInfo> = emptyMap()
            override fun routeAliasesKV(): Map<String, KClass<*>> = emptyMap()
            override fun globalPushInterceptors(): List<GlobalPushInterceptorInfo> =
                listOf(GlobalPushInterceptorInfo(resolvePushInterceptor(KoinDiPush::class), 0))
            override fun globalPopInterceptors(): List<GlobalPopInterceptorInfo> = emptyList()
            override fun entryProviderInstaller(): EntryProviderInstaller = {}
        }
        val navigator = Navigator(Root())
        try {
            // Navigator 构造期已完成解析（DI-only 未抛错即 Koin 命中），但尚未执行。
            assertEquals(0, provided.hits)
            navigator.push { destination = PageA() }
            runCurrent()
            // Koin single 实例执行了拦截，依赖已注入。
            assertEquals(1, provided.hits)
            assertEquals(PageA::class, navigator.last()!!::class)
        } finally {
            navigator.core.cancel()
            RouterSingleton.resetForTest()
            RouterSingleton.providers.addAll(providers)
            koin.close()
            Dispatchers.resetMain()
        }
    }
}
