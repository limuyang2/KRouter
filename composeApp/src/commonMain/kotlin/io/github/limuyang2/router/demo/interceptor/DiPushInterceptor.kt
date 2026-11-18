package io.github.limuyang2.router.demo.interceptor

import io.github.limuyang2.router.IRouterPushInterceptor
import io.github.limuyang2.router.PushResponse
import io.github.limuyang2.router.anno.RouterGlobalPushInterceptor
import io.github.limuyang2.router.demo.DemoSession

/**
 * @author 李沐阳
 * @date 2026/11/18
 * @description DI 演示拦截器：构造带参、没有无参构造，只能由 initRouter 配置的
 * interceptorResolver（见 DemoDependencyBox）提供实例，验证注解声明路径吃 DI。
 */
@RouterGlobalPushInterceptor(priority = 100)
class DiPushInterceptor(private val session: DemoSession) : IRouterPushInterceptor {

    var hits = 0
        private set

    override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
        hits++
        println("--------------> DI interceptor, session=${session.label}")
        return chain.proceed(chain.request())
    }
}
