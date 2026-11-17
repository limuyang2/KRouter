package io.github.limuyang2.router.anno

import kotlin.reflect.KClass

/**
 * 声明路由页面。
 *
 * 被注解的类应实现 [io.github.limuyang2.router.IRouterEntryProvider]，KSP 处理器据此生成路由信息、
 * 实例工厂以及页面注册代码。
 *
 * Declares a routing destination.
 *
 * Annotated classes should implement [io.github.limuyang2.router.IRouterEntryProvider]. KSP generates
 * route metadata, instance factories, and registration code based on this annotation.
 *
 * @param path 路由路径，如 `/home` 或 `/detail`；为空时不生成 URL 映射 / Route path, e.g. `/home` or `/detail`; empty generates no URL mapping.
 * @param scheme 路由协议，为空时使用全局默认 scheme / Route scheme; empty uses the global default scheme.
 * @param host 路由主机名，如 `app` / Route host, e.g. `app`.
 * @param desc 页面描述信息 / Destination description.
 * @param pushInterceptors 绑定的页面级 Push 拦截器列表 / List of page-level push interceptors bound to this destination.
 * @param pushInterceptorPriorities Push 拦截器优先级数组，与 [pushInterceptors] 按下标对应，数值越小越先执行 / Priorities for push interceptors matching [pushInterceptors] by index; smaller values run first.
 * @param popInterceptors 绑定的页面级 Pop 拦截器列表 / List of page-level pop interceptors bound to this destination.
 * @param popInterceptorPriorities Pop 拦截器优先级数组，与 [popInterceptors] 按下标对应，数值越小越先执行 / Priorities for pop interceptors matching [popInterceptors] by index; smaller values run first.
 * @param singleTask 页面级 singleTask 默认值：push 该页面时若栈中已存在同类型实例，则移动已有实例到栈顶
 * （保留实例身份与页面状态，不重新注入本次参数），其上方页面按 Cancelled 结算移除；不存在时正常入栈。
 * push builder 可通过 `singleTask(...)` 显式开启或关闭以覆盖此默认值。同一类多条 [Router] 声明的该值必须一致。
 * / Page-level singleTask default: when pushing this destination, if an instance of the same class already
 * exists in the back stack, move that existing instance to the top (instance identity and page state are
 * preserved; the current push parameters are not re-injected) and remove the pages above it as Cancelled;
 * otherwise push normally. The push builder can override this default via `singleTask(...)`. Repeated
 * [Router] declarations on one class must agree on this value.
 */
@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Router(
    val path: String = "",
    val scheme: String = "",
    val host: String = "",
    val desc: String = "",
    val pushInterceptors: Array<KClass<*>> = [],
    val pushInterceptorPriorities: IntArray = [],
    val popInterceptors: Array<KClass<*>> = [],
    val popInterceptorPriorities: IntArray = [],
    val singleTask: Boolean = false,
)
