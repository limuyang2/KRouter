package io.github.limuyang2.router

/** 出栈拦截结果。放行及重定向由 Chain 创建，调用方不能跳过解析直接指定删除目标。 */
sealed class PopResponse private constructor() {
    data object Interrupted : PopResponse()

    internal class Allowed(val request: PopRequest) : PopResponse()
    internal class Redirected(val from: PopRequest, val popType: PopType) : PopResponse()
}
