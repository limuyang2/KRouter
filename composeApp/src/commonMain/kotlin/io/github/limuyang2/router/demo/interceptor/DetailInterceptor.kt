package io.github.limuyang2.router.demo.interceptor

import io.github.limuyang2.router.IRouterPopInterceptor
import io.github.limuyang2.router.IRouterPushInterceptor
import io.github.limuyang2.router.PopResponse
import io.github.limuyang2.router.PushResponse
import io.github.limuyang2.router.anno.RouterOwnerPopInterceptor
import io.github.limuyang2.router.anno.RouterOwnerPushInterceptor
import io.github.limuyang2.router.demo.Router
import io.github.limuyang2.router.demo.TestData

class DetailPopInterceptor : IRouterPopInterceptor {

    override suspend fun intercept(chain: IRouterPopInterceptor.Chain): PopResponse {
        println("Detail pop: ${chain.request().operation}, pages=${chain.request().removeTargets}")
        return chain.proceed()
    }
}

object DetailPushInterceptor : IRouterPushInterceptor {

    override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
        println("--------------------->>>>>  Detail push Interceptor")
        val request = chain.request().newBuilder()
            .setExtra("names", TestData("@Router interceptor data"))
            .build()
        return chain.proceed(request)
    }
}

@RouterOwnerPopInterceptor(owners = [Router.OwnerDetail::class, Router.Detail::class])
class OwnerDetailPopInterceptor : IRouterPopInterceptor {

    override suspend fun intercept(chain: IRouterPopInterceptor.Chain): PopResponse {
        println("Owner detail pop: ${chain.request().operation}, pages=${chain.request().removeTargets}")
        return chain.proceed()
    }
}

@RouterOwnerPushInterceptor(owners = [Router.OwnerDetail::class, Router.Detail::class])
object OwnerDetailPushInterceptor : IRouterPushInterceptor {

    override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
        println("--------------------->>>>>  Owner detail push Interceptor")
        val request = chain.request().newBuilder()
            .setExtra("names", TestData("Owner interceptor data"))
            .build()
        return chain.proceed(request)
    }
}
