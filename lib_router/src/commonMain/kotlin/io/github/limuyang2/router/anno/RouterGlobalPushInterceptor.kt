package io.github.limuyang2.router.anno

/**
 * 声明全局 Push 拦截器。
 *
 * 适用于在页面跳转前执行统一的参数注入、重定向或拦截校验逻辑。
 *
 * Declares a global push interceptor.
 *
 * Used for unified parameter injection, redirection, or interception checks before navigation.
 *
 * @param priority 优先级，数值越小优先级越高、越先执行，默认值为 10 / Priority; smaller values indicate higher priority and execute earlier, default is 10.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RouterGlobalPushInterceptor(
    val priority: Int = 10
)
