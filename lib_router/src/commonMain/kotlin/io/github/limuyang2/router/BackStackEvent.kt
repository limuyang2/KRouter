package io.github.limuyang2.router

/**
 * 导航栈变更事件，通过 [Navigator.backStackEvents] 发射。
 *
 * Back-stack mutation event emitted through [Navigator.backStackEvents].
 */
sealed interface BackStackEvent {

    /**
     * 本次事件操作的页面实例
     *
     * Destination entry affected by this event.
     */
    val entry: IRouterEntryProvider

    /**
     * 事件发生后的当前导航栈快照
     *
     * Current back-stack snapshot after this event occurred.
     */
    val backStack: List<IRouterEntryProvider>

    /**
     * 页面入栈事件
     *
     * Push event emitted when an entry is added to the stack.
     */
    data class Push(
        override val entry: IRouterEntryProvider,
        override val backStack: List<IRouterEntryProvider>,
    ) : BackStackEvent

    /**
     * 页面出栈事件（包括 pop、remove、backTo、清空栈等所有移除场景）
     *
     * Pop event emitted when an entry is removed from the stack (including pop, remove, backTo, or stack clear).
     */
    data class Pop(
        override val entry: IRouterEntryProvider,
        override val backStack: List<IRouterEntryProvider>,
    ) : BackStackEvent
}
