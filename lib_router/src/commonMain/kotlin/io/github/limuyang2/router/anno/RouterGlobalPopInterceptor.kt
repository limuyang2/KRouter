package io.github.limuyang2.router.anno

/**
 * 声明全局 Pop 拦截器。
 *
 * 适用于在页面关闭或返回前执行统一的确认、重定向或拦截校验逻辑。
 *
 * Declares a global pop interceptor.
 *
 * Used for unified confirmation, redirection, or interception checks before page popping or removal.
 *
 * @param priority 优先级，数值越小优先级越高、越先执行，默认值为 10 / Priority; smaller values indicate higher priority and execute earlier, default is 10.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RouterGlobalPopInterceptor(
    val priority: Int = 10
)
