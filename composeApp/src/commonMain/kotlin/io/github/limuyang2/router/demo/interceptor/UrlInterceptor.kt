package io.github.limuyang2.router.demo.interceptor

import io.github.limuyang2.router.IRouterPushInterceptor
import io.github.limuyang2.router.PushResponse
import io.github.limuyang2.router.anno.RouterGlobalPushInterceptor
import io.github.limuyang2.router.demo.Router

/**
 * @author 李沐阳
 * @date 2025/12/24
 * @description
 */
@RouterGlobalPushInterceptor(priority = 10)
class UrlInterceptor : IRouterPushInterceptor {
    override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
        val request = chain.request()

        println("--------------> UrlInterceptor 执行了    ${request.destination}")


        if (request.destination is Router.Web) {
            return chain.proceed(request)
        }

        println("--------------> originalUri： ${request.originalUri}  uri : ${request.uri}" + "   scheme:${request.uri?.scheme}          ${request.destination}")


        val uri = request.originalUri

        if (uri != null) {
            val scheme = uri.scheme
            if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
            ) {
                val newRequest = request.newBuilder()
                    .destination(Router.Web(uri.toString()))
                    .build()

                return chain.proceed(newRequest)
            }
        }

        return chain.proceed(request)
    }
}