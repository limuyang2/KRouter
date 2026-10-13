package io.github.limuyang2.router.demo

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.limuyang2.router.Navigator
import io.github.limuyang2.router.RouteResult
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Exercises the callback captured before a caller leaves Composition. */
@OptIn(ExperimentalCoroutinesApi::class)
class CallbackPresentationTest {
    @Test
    fun detailCallbackResultIsVisibleToRecreatedCompositionAndIsolatedFromOtherInstances() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recomposer = Recomposer(coroutineContext)
        val compositions = mutableListOf<Composition>()
        try {
            initDemoRouter()
            val caller = Router.Detail(persistentListOf(1))
            val other = Router.Detail(persistentListOf(1))
            val navigator = Navigator(caller)
            navigator.backStack
            runCurrent()
            var callback: (RouteResult<DetailResult>) -> Unit = {}
            var displayed: String? = null
            fun composeCaller() = Composition(EmptyApplier(), recomposer).also { composition ->
                compositions += composition
                composition.setContent {
                    var lastResult by caller.resultState.result
                    callback = { lastResult = it.toString() }
                    SideEffect { displayed = lastResult }
                }
            }

            val original = composeCaller()
            navigator.pushForResult(Router.Result(null), onResult = callback)
            runCurrent()
            // NavDisplay disposes the covered caller; the pending request still holds its callback.
            original.dispose()
            navigator.pop(DetailResult("from result"))
            runCurrent()
            assertSame(caller, navigator.last())
            composeCaller()
            assertEquals("Success(value=DetailResult(str=from result))", displayed)
            assertNull(other.resultState.result.value)
        } finally {
            compositions.forEach { it.dispose() }
            recomposer.cancel()
            Dispatchers.resetMain()
        }
    }
}

private class EmptyApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}
