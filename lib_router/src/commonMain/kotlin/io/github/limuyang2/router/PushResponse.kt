package io.github.limuyang2.router

class PushResponse internal constructor(
    val request: PushRequest,
    val destination: IRouterEntryProvider,
    val routeBeforeCallback: (() -> Unit)?,
    val routeSuccessCallback: (() -> Unit)?,
    val routeFailureCallback: (() -> Unit)?,
    val routeCompletedCallback: (() -> Unit)?,
    val preventDuplicates: Boolean,
    val isCleanStack: Boolean,
    val isPopCurrent: Boolean,
    internal val isInterrupted: Boolean = false
)
