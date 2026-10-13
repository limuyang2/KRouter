package io.github.limuyang2.router.demo.interceptor

import io.github.limuyang2.router.IRouterPushInterceptor
import io.github.limuyang2.router.PushResponse
import io.github.limuyang2.router.anno.RouterGlobalPushInterceptor

/**
 * @author 李沐阳
 * @date 2025/12/24
 * @description
 */
@RouterGlobalPushInterceptor(priority = 1)
class TestInterceptor: IRouterPushInterceptor {
    override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
        val request = chain.request()
        println("--------------> test Interceptor")

        return chain.proceed(request)
    }
}