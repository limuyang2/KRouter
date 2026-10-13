package io.github.limuyang2.router.info

import io.github.limuyang2.router.IRouterInstance

data class ParseRouterInfo(
    val url: String,
    val scheme: String,
    val host: String,
    val path: String,
    val desc: String,
    val pushInterceptors: List<ParsePagePushInterceptorInfo>,
    val popInterceptors: List<ParsePagePopInterceptorInfo>,
    val instanceProvider: IRouterInstance
)
