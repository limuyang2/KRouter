package io.github.limuyang2.router

/** 本次请求要执行哪一种返回操作。 */
enum class PopOperation {
    /** 对应 pop() / top(result)：关闭发起请求时的当前页，可向打开它的页面返回结果。 */
    CURRENT,

    /** 对应 last(...) / all(...)：移除匹配的页面，不携带业务返回值。 */
    REMOVE,

    /** 对应 to(...)：保留指定页面，移除请求解析时位于它上方的页面。 */
    BACK_TO,
}

/**
 * 一次已经确定要关闭哪些页面的返回请求，拦截器通过 chain.request() 获取。
 *
 * [removeTargets] 告诉拦截器要关闭哪些实例；[backToPage] 表示 to(...) 要保留的页面；
 * [hasResult] 和 [result] 表示调用方是否携带业务返回值，以及具体的值。
 * 改变关闭目标使用 chain.redirect { ... }；仅改变返回值使用 [withResult]。
 */
class PopRequest internal constructor(
    internal val popType: PopType,
    internal val plan: PopPlan,
) {
    /** 调用方使用的操作种类，具体对应关系见 [PopOperation]；实际要关闭的实例见 [removeTargets]。 */
    val operation: PopOperation
        get() = when (popType) {
            is PopType.Current -> PopOperation.CURRENT
            is PopType.Remove -> PopOperation.REMOVE
            is PopType.BackTo -> PopOperation.BACK_TO
        }

    /**
     * 本次待移除的目标实例，按请求解析时从栈顶到栈底的顺序排列。
     *
     * 例如栈为 `[A, B, C]`：pop() 的目标是 `[C]`，to(A) 的目标是 `[C, B]`。
     * 目标在进入拦截器前绑定；等待期间新增的页面不会加入此列表。
     * BACK_TO 要保留的接收页不在列表中，可通过 [backToPage] 获取。
     * 任一目标提前离栈，本次请求即失效；同一实例重新入栈也不会恢复旧请求。
     * 返回列表副本，修改目标应通过 Chain.redirect，不能通过修改此列表实现。
     */
    val removeTargets: List<IRouterEntryProvider> get() = plan.removeTargets.toList()

    /**
     * 调用 to(...) 时指定要回退到的页面实例。该页面会保留在栈中，不属于 [removeTargets]。
     *
     * 例如栈为 `[A, B, C]`，调用 to(A)：此属性是 A，要关闭的页面是 C、B。
     * 即使 to(A) 没有携带返回值，此属性仍然是 A；若携带结果，接收页面也是这个 A。
     *
     * 只有 [PopOperation.BACK_TO] 有值。普通 pop() 或 last/all 操作时为 null；
     * 因此它不表示每一次返回操作之后的栈顶，也不表示普通 pop(result) 的结果接收页。
     * 等待期间若新增 D，to(A) 放行后会保留 D，此属性依旧指向最初绑定的 A。
     */
    val backToPage: IRouterEntryProvider? get() = plan.backToPage

    /**
     * 调用方是否明确提供了业务返回值，用于区分没有返回值与明确返回 null。
     *
     * - pop() / top() / to(A)：false。
     * - pop(42) / top(42) / to(A, 42, key)：true。
     * - pop(result = null) / top(null)：true，此时 [result] 为 null。
     * - last(...) / all(...)：始终为 false。
     *
     * 此属性只描述请求是否携带值，不表示返回操作已经成功或结果已经送达。
     */
    val hasResult: Boolean get() = providedResult != null

    /**
     * 本次返回请求携带的业务值，例如选中的商品 ID、编辑后的数据或确认结果。
     * 这是原始业务值，不是表示成功/取消等状态的 [RouteResult]。
     *
     * 值来自 pop(value)、top(value) 或 to(..., result = value, key = ...)：
     * - CURRENT：交给最初打开待关闭页面、并通过 pushForResult 等入口等待结果的页面。
     *   例如 A 打开 B 并等待结果，B 调用 pop(42)，此属性是 42，接收方是 A。
     * - BACK_TO：直接交给 [backToPage] 上匹配调用时所指定 key 和结果类型的 consumer。
     *   例如 C 调用 to(A, 42, "refresh")，此属性是 42，接收方是 A 的 refresh consumer。
     * - REMOVE：不携带业务返回值，此属性为 null。
     *
     * 为 null 时必须结合 [hasResult]：false 表示没有提供值，true 表示明确返回 null。
     * 拦截器放行并完成页面移除后才会结算结果，接收页回到前台后才消费；
     * 请求被中断或失效时不会因读取此属性而投递结果。接收端仍会校验类型，
     * CURRENT 没有等待结果的请求或 BACK_TO 的目标 entry 从未注册匹配 consumer 时，不会投递业务值。
     * 已注册的 consumer 暂时离开 Composition 时，结果会等待该 entry 回到前台并重新绑定后消费。
     */
    val result: Any? get() = providedResult?.value

    private val providedResult: PopResult.Provided?
        get() = when (val type = popType) {
            is PopType.Current -> type.result as? PopResult.Provided
            is PopType.BackTo -> type.directResult?.result
            is PopType.Remove -> null
        }

    /**
     * 创建一个使用新业务返回值的请求，保留原先绑定的页面，不修改当前请求。
     * 在拦截器中使用 chain.proceed(request.withResult(value)) 才会将新值传给后续处理。
     *
     * @param result 新的业务返回值。传 null 表示明确返回 null，新请求的 [hasResult] 为 true。
     * @return 绑定相同页面、携带新返回值的请求，接收端仍按原先声明的结果类型校验。
     * @throws IllegalArgumentException BACK_TO 原请求没有通过 to(..., result, key) 配置接收位置。
     * @throws IllegalStateException 当前是 REMOVE 操作，不支持业务返回值。
     */
    fun withResult(result: Any?): PopRequest {
        val updated = when (val type = popType) {
            is PopType.Current -> type.copy(result = PopResult.Provided(result))
            is PopType.BackTo -> type.copy(
                directResult = requireNotNull(type.directResult) {
                    "Use redirect { to(..., result, key) } to configure a BackTo result receiver."
                }.copy(result = PopResult.Provided(result)),
            )
            is PopType.Remove -> error("Remove does not support a return value.")
        }
        return PopRequest(updated, plan)
    }
}

/**
 * 一次解析绑定的页面生命周期；任一目标或 BackTo 接收页离栈后，整个记录失效。
 *
 * @param removeTargets 已绑定的待移除实例，按解析时从栈顶到栈底排列。
 * @param backToPage BACK_TO 要保留的接收页，其它操作为 null。
 * @param requestResultVictim 通过原 caller 请求结算主动返回结果的页面；没有则为 null。
 */
internal class PopPlan(
    val removeTargets: List<IRouterEntryProvider>,
    val backToPage: IRouterEntryProvider?,
    val requestResultVictim: IRouterEntryProvider?,
) {
    var isValid: Boolean = true
}
