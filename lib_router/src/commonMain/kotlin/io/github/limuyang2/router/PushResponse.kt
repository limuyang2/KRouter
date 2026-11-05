package io.github.limuyang2.router

/**
 * Push 拦截器链处理后的响应结果。
 *
 * 封装了最终入栈的页面实例、导航配置选项以及关联的回调函数。通常通过
 * [IRouterPushInterceptor.Chain.proceed] 或 [IRouterPushInterceptor.Chain.interrupt] 构造。
 *
 * Response produced after processing through the push interceptor chain.
 *
 * Encapsulates the resolved destination entry, navigation options, and associated callbacks.
 * Typically constructed via [IRouterPushInterceptor.Chain.proceed] or [IRouterPushInterceptor.Chain.interrupt].
 *
 * @param request 关联的不可变请求 / Associated immutable push request.
 * @param destination 解析或指定的入栈页面实例 / Resolved or specified destination entry to be pushed.
 * @param routeBeforeCallback 页面入栈前的生命周期回调 / Lifecycle callback invoked before the entry is added to the stack.
 * @param routeSuccessCallback 页面入栈成功后的生命周期回调 / Lifecycle callback invoked after the entry is successfully added.
 * @param routeFailureCallback 跳转失败或被中断时的生命周期回调 / Lifecycle callback invoked when the push fails or is interrupted.
 * @param routeCompletedCallback 跳转完成（无论成功与否）后的生命周期回调 / Lifecycle callback invoked after the push completes.
 * @param preventDuplicates 是否阻止栈顶相同类型页面重复跳转 / Whether duplicate push of the same route type at the top is prevented.
 * @param isCleanStack 是否在成功跳转后清空旧导航栈 / Whether old entries are cleared from the back stack after a successful push.
 * @param isPopCurrent 是否在成功打开新页面后关闭旧 current / Whether the previous current page is closed after opening the new destination.
 */
class PushResponse internal constructor(
    val request: PushRequest,
    val destination: IRouterEntryProvider,
    val routeBeforeCallback: (() -> Unit)?,
    val routeSuccessCallback: (() -> Unit)?,
    val routeFailureCallback: (() -> Unit)?,
    val routeCompletedCallback: (() -> Unit)?,
    val preventDuplicates: Boolean,
    val isCleanStack: Boolean,
    val isPopCurrent: Boolean,
    internal val isInterrupted: Boolean = false
)
