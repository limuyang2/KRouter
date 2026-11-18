package io.github.limuyang2.router.demo

import io.github.limuyang2.router.demo.interceptor.DiPushInterceptor
import kotlin.reflect.KClass

/**
 * 演示用的极简 DI 容器：KClass 到单例实例的映射，未知类返回 null。
 * demo 不引入 Koin，用一个手写 map 演示 interceptorResolver 的接法；
 * 真实项目中等价于 `interceptorResolver { clazz -> koin.getOrNull(clazz) }`。
 *
 * Minimal DI container for the demo: a KClass-to-singleton map returning null
 * for unknown classes. Stands in for Koin, whose equivalent wiring is
 * `interceptorResolver { clazz -> koin.getOrNull(clazz) }`.
 */
object DemoDependencyBox {
    /** 模拟被注入的业务依赖 / Simulated injected business dependency. */
    val session = DemoSession()

    private val diPushInterceptor = DiPushInterceptor(session)

    private val registry: Map<KClass<*>, Any> = mapOf(
        DiPushInterceptor::class to diPushInterceptor,
    )

    /** resolver 命中次数，供测试断言生成代码确实走了 resolver / Hit count for tests. */
    var resolutions = 0
        private set

    /** resolver 提供的 DI 拦截器已执行的次数 / Executions of the resolver-supplied interceptor. */
    val diPushHits: Int get() = diPushInterceptor.hits

    fun resolve(clazz: KClass<*>): Any? {
        return registry[clazz]?.also { resolutions++ }
    }
}

/**
 * 模拟 DI 构造注入的依赖载荷。
 *
 * Simulated payload injected through DI construction.
 */
class DemoSession(val label: String = "demo-session")
