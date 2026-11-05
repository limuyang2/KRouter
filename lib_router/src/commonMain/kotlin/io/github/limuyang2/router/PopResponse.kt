package io.github.limuyang2.router

/**
 * 出栈拦截结果。放行及重定向由 Chain 创建，调用方不能跳过解析直接指定删除目标。
 *
 * Pop interception result. Continuations and redirections are created by the Chain; callers cannot
 * bypass target resolution to specify removal targets directly.
 */
sealed class PopResponse private constructor() {
    /**
     * 表示返回操作被中断，不移除页面且不派发业务结果。
     *
     * Indicates that the pop operation was interrupted; no pages are removed and no business result is delivered.
     */
    data object Interrupted : PopResponse()

    internal class Allowed(val request: PopRequest) : PopResponse()
    internal class Redirected(val from: PopRequest, val popType: PopType) : PopResponse()
}
