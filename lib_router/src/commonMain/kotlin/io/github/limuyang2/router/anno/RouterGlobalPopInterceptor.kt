package io.github.limuyang2.router.anno

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RouterGlobalPopInterceptor(
    val priority: Int = 10
)
