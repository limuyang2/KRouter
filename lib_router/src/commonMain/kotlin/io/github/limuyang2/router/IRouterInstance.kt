package io.github.limuyang2.router

/**
 * 路由注解类的接口。用于生成类的实例。
 *
 * KSP 会为每个需要参数注入的 @Router 类生成一个此接口的实现。
 *
 * Instance factory implemented by KSP-generated classes for @Router destinations requiring parameter injection.
 */
interface IRouterInstance {

    /**
     * 根据传入的参数，生成路由对象的实例
     *
     * Creates a destination instance from the supplied parameters using the generated injection rules.
     *
     * @param extras 参数通道，包含 URL 参数和手动对象参数 / Parameter map containing parsed URL parameters and explicit objects.
     * @return 注入参数后的页面实例 / Destination instance with injected parameters.
     */
    fun instance(extras: Map<String, Any>?): IRouterEntryProvider
}
