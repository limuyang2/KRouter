package io.github.limuyang2.router

/**
 * 路由注解类的接口。用于生成类的实例。
 *
 * KSP 会为每个需要参数注入的 @Router 类生成一个此接口的实现。
 */
interface IRouterInstance {

    /**
     * 根据传入的参数，生成路由对象的实例
     *
     * @param extras 参数通道，包含 URL 自动解析的字符串参数和手动传入的对象参数
     */
    fun instance(extras: Map<String, Any>?): IRouterEntryProvider
}
