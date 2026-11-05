package io.github.limuyang2.router.anno

/**
 * 声明路由参数注入。
 *
 * 可标记在主/次构造函数的参数上，或类内部的 var 属性上。在通过 URL 或 extras 跳转时，
 * KSP 生成的实例工厂会自动将同名参数注入到对应的参数或属性中。
 *
 * Declares route parameter injection.
 *
 * Can be applied to primary/secondary constructor parameters, or to mutable properties inside the class.
 * When navigating via URL or extras, the KSP-generated instance factory automatically injects matching
 * parameters into the corresponding parameter or property.
 *
 * @param name 参数名称，对应 URL 查询参数或 extras 字典中的 key / Parameter name matching the URL query parameter or extras map key.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class RouterParam(val name: String)
