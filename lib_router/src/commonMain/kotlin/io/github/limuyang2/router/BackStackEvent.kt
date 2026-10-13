package io.github.limuyang2.router

/**
 * 栈变更事件，通过 [Navigator.backStackEvents] 发射。
 */
sealed interface BackStackEvent {

    /**
     * 被操作的 entry
     */
    val entry: IRouterEntryProvider

    /**
     * 事件发生后的当前栈快照
     */
    val backStack: List<IRouterEntryProvider>

    /**
     * 页面入栈
     */
    data class Push(
        override val entry: IRouterEntryProvider,
        override val backStack: List<IRouterEntryProvider>,
    ) : BackStackEvent

    /**
     * 页面出栈（包括 pop、remove、backTo、清空栈等所有移除场景）
     */
    data class Pop(
        override val entry: IRouterEntryProvider,
        override val backStack: List<IRouterEntryProvider>,
    ) : BackStackEvent
}
