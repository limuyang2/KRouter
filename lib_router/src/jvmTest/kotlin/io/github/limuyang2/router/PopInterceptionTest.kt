package io.github.limuyang2.router

import io.github.limuyang2.router.info.ParsePagePopInterceptorInfo
import io.github.limuyang2.router.info.ParseRouterInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PopInterceptionTest {
    private class Root : IRouterEntryProvider
    private class Page : IRouterEntryProvider
    private class Other : IRouterEntryProvider
    private data class EqualPage(val value: Int) : IRouterEntryProvider

    @Test
    fun pendingCurrentRemovesOriginalPageAndDelaysResultUntilCallerReturns() {
        val gate = CompletableDeferred<Unit>()
        var request: PopRequest? = null
        val interceptor = interceptor {
            request = request()
            gate.await()
            proceed()
        }
        withRouter(interceptor) { navigator, root ->
            val b = Page()
            val c = Other()
            val results = mutableListOf<RouteResult<Int>>()
            navigator.push { destination = b; onRouteResult<Int> { results += it } }
            runCurrent()
            navigator.pop { top(42) }
            runCurrent()
            val captured = requireNotNull(request)
            assertEquals(PopOperation.CURRENT, captured.operation)
            assertSame(b, captured.removeTargets.single())
            assertTrue(captured.hasResult)
            assertEquals(42, captured.result)
            navigator.push { destination = c }
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            assertStack(navigator, root, c)
            assertTrue(results.isEmpty())
            navigator.pop { greenChannel = true }
            runCurrent()
            assertEquals<List<RouteResult<Int>>>(listOf(RouteResult.Success(42)), results)
        }
    }

    @Test
    fun interruptedPopLeavesOriginalAndNewPageUntouched() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); interrupt() }) { navigator, root ->
            val b = Page(); val c = Other()
            navigator.push { destination = b }; runCurrent()
            navigator.pop(); runCurrent()
            navigator.push { destination = c }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(navigator, root, b, c)
        }
    }

    @Test
    fun duplicatePendingPopsRemoveTheirTargetOnlyOnce() {
        val gate = CompletableDeferred<Unit>()
        var interceptions = 0
        withRouter(interceptor { interceptions++; gate.await(); proceed() }) { navigator, root ->
            navigator.push { destination = Page() }; runCurrent()
            navigator.pop(); navigator.pop(); runCurrent()
            val c = Other()
            navigator.push { destination = c }; runCurrent()
            assertEquals(2, interceptions)
            gate.complete(Unit); runCurrent()
            assertStack(navigator, root, c)
        }
    }

    @Test
    fun removedThenReinsertedInstanceIsNotDeletedByOldResponse() {
        val gate = CompletableDeferred<Unit>()
        // Even a response already approved by the tail of the chain must be revalidated at commit.
        withRouter(interceptor { val response = proceed(); gate.await(); response }) { navigator, root ->
            val b = Page()
            navigator.push { destination = b }; runCurrent()
            navigator.pop(); runCurrent()
            navigator.pop { greenChannel = true }; runCurrent()
            navigator.push { destination = b }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(navigator, root, b)
        }
    }

    @Test
    fun cleanStackInvalidatesPendingPop() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { navigator, _ ->
            val b = Page(); val c = Other()
            navigator.push { destination = b }; runCurrent()
            navigator.pop(); runCurrent()
            navigator.push { destination = c; isCleanStack = true }; runCurrent()
            navigator.push { destination = b }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(navigator, c, b)
        }
    }

    @Test
    fun redirectRebuildsGlobalAndNewTargetInterceptorsAndHonorsRejection() {
        lateinit var b: Page
        val calls = mutableListOf<String>()
        val global = interceptor {
            calls += "global:${request().operation}"
            if (request().operation == PopOperation.CURRENT) redirect { last(b) } else proceed()
        }
        withRouter(global, mapOf(Page::class to interceptor { calls += "B"; interrupt() },
            Other::class to interceptor { calls += "C"; proceed() })) { navigator, root ->
            b = Page(); val c = Other()
            navigator.push { destination = b }; navigator.push { destination = c }; runCurrent()
            navigator.pop(); runCurrent()
            assertEquals(listOf("global:CURRENT", "global:REMOVE", "B"), calls)
            assertStack(navigator, root, b, c)
        }
    }

    @Test
    fun redirectFromPageInterceptorCanRemoveDifferentPage() {
        lateinit var b: Page
        var bChecked = false
        withRouter(pageInterceptors = mapOf(
            Other::class to interceptor { redirect { last(b) } },
            Page::class to interceptor { bChecked = true; assertSame(b, request().removeTargets.single()); proceed() },
        )) { navigator, root ->
            b = Page(); val c = Other()
            navigator.push { destination = b }; navigator.push { destination = c }; runCurrent()
            navigator.pop(); runCurrent()
            assertTrue(bChecked)
            assertStack(navigator, root, c)
        }
    }

    @Test
    fun unchangedRedirectKeepsBoundTargetAndDoesNotRestartChain() {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        withRouter(interceptor { calls++; gate.await(); redirect { top(9) } }) { navigator, root ->
            val results = mutableListOf<RouteResult<Int>>()
            val c = Other()
            navigator.push { destination = Page(); onRouteResult<Int> { results += it } }; runCurrent()
            navigator.pop(); runCurrent()
            navigator.push { destination = c }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertEquals(1, calls)
            assertStack(navigator, root, c)
            navigator.pop { greenChannel = true }; runCurrent()
            assertEquals<List<RouteResult<Int>>>(listOf(RouteResult.Success(9)), results)
        }
    }

    @Test
    fun withResultPreservesTargetAndDistinguishesNullFromNoResult() {
        var requestHasResult = true
        withRouter(interceptor {
            requestHasResult = request().hasResult
            proceed(request().withResult(null))
        }) { navigator, _ ->
            val results = mutableListOf<RouteResult<Int>>()
            navigator.push { destination = Page(); onRouteResult<Int> { results += it } }; runCurrent()
            navigator.pop(); runCurrent()
            assertFalse(requestHasResult)
            assertEquals<List<RouteResult<Int>>>(listOf(RouteResult.Success<Int>(null)), results)
        }
    }

    @Test
    fun changedResultStillUsesOriginalExpectedType() = withRouter(interceptor {
        proceed(request().withResult("wrong type"))
    }) { navigator, _ ->
        val results = mutableListOf<RouteResult<Int>>()
        navigator.push { destination = Page(); onRouteResult<Int> { results += it } }; runCurrent()
        navigator.pop { top(1) }; runCurrent()
        assertTrue(results.single() is RouteResult.TypeMismatch)
    }

    @Test
    fun redirectLoopStopsWithoutMutatingStack() {
        lateinit var b: Page
        var calls = 0
        withRouter(interceptor {
            calls++
            if (request().operation == PopOperation.CURRENT) redirect { last(b) } else redirect { top() }
        }) { navigator, root ->
            b = Page()
            navigator.push { destination = b }; runCurrent()
            navigator.pop(); runCurrent()
            assertTrue(calls in 2..17)
            assertStack(navigator, root, b)
        }
    }

    @Test
    fun redirectToMissingTargetDoesNotFallBackToCurrent() = withRouter(interceptor {
        redirect { last(Page()) }
    }) { navigator, root ->
        val b = Page()
        navigator.push { destination = b }; runCurrent()
        navigator.pop(); runCurrent()
        assertStack(navigator, root, b)
    }

    @Test
    fun removeAllBindsOnlyInitiallyMatchedPagesAndExcludesRootFromInterception() {
        val gate = CompletableDeferred<Unit>()
        var seen: List<IRouterEntryProvider> = emptyList()
        withRouter(interceptor { seen = request().removeTargets; gate.await(); proceed() }) { navigator, root ->
            val b = Page(); val c = Other(); val d = Page()
            navigator.push { destination = b }; navigator.push { destination = c }; runCurrent()
            navigator.pop { all(Root::class, Page::class, Other::class) }; runCurrent()
            assertEquals(listOf(c, b), seen)
            navigator.push { destination = d }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(navigator, root, d)
        }
    }

    @Test
    fun removingOneMemberInvalidatesWholePendingBatch() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { navigator, root ->
            val b = Page(); val c = Other()
            navigator.push { destination = b }; navigator.push { destination = c }; runCurrent()
            navigator.pop { all(Page::class, Other::class) }; runCurrent()
            navigator.pop { last(b); greenChannel = true }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(navigator, root, c)
        }
    }

    @Test
    fun commitRetainsCurrentRootIfOriginalRootWasRemovedWhileWaiting() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { navigator, root ->
            val b = Page(); val c = Other()
            navigator.push { destination = b }; navigator.push { destination = c }; runCurrent()
            navigator.pop { all(Page::class, Other::class) }; runCurrent()
            navigator.pop { last(root); greenChannel = true }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(navigator, b)
        }
    }

    @Test
    fun backToUsesOriginalReceiverAndDefersDirectResultWhileNewPageIsOnTop() {
        val gate = CompletableDeferred<Unit>()
        var request: PopRequest? = null
        withRouter(interceptor { request = request(); gate.await(); proceed() }) { navigator, root ->
            val results = mutableListOf<RouteResult<Any>>()
            val slot = buildRouteResultSlotId("refresh", Int::class)
            navigator.routeResultController.registerConsumer(root.contentKey(), slot) { results += it }
            val b = Page(); val c = Other(); val d = Page()
            navigator.push { destination = b }; navigator.push { destination = c }; runCurrent()
            navigator.pop { to(root, 42, "refresh") }; runCurrent()
            assertSame(root, request!!.backToPage)
            assertEquals(listOf(c, b), request!!.removeTargets)
            navigator.push { destination = d }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(navigator, root, d)
            assertTrue(results.isEmpty())
            // Registration while the owner is still behind D must not consume its inbox.
            navigator.routeResultController.unregisterConsumer(root.contentKey(), slot)
            navigator.routeResultController.registerConsumer(root.contentKey(), slot) { results += it }
            runCurrent()
            assertTrue(results.isEmpty())
            navigator.pop { greenChannel = true }; runCurrent()
            assertEquals<List<RouteResult<Any>>>(listOf(RouteResult.Success(42)), results)
        }
    }

    @Test
    fun removingBackToReceiverInvalidatesPendingRequest() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { navigator, root ->
            val b = Page(); val c = Other()
            navigator.push { destination = b }; navigator.push { destination = c }; runCurrent()
            navigator.pop { to(root) }; runCurrent()
            navigator.pop { last(root); greenChannel = true }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(navigator, b, c)
        }
    }

    @Test
    fun instanceSelectionAndRedirectComparisonUseReferenceIdentity() {
        lateinit var first: EqualPage
        lateinit var second: EqualPage
        var calls = 0
        withRouter(interceptor {
            calls++
            if (request().removeTargets.single() === first) redirect { last(second) } else proceed()
        }) { navigator, root ->
            first = EqualPage(1); second = EqualPage(1)
            navigator.push { destination = first }; navigator.push { destination = second }; runCurrent()
            navigator.pop { last(first) }; runCurrent()
            assertEquals(2, calls)
            assertStack(navigator, root, first)
        }
    }

    @Test
    fun backToInstanceKeepsExactEqualInstance() = withRouter { navigator, root ->
        val first = EqualPage(1); val second = EqualPage(1)
        navigator.push { destination = first }; navigator.push { destination = second }; runCurrent()
        navigator.pop { to(first) }; runCurrent()
        assertStack(navigator, root, first)
    }

    @Test
    fun noResultAndPopEventReferToOriginalPageAfterNewPush() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { navigator, root ->
            val results = mutableListOf<RouteResult<Int>>()
            val events = mutableListOf<BackStackEvent>()
            backgroundScope.launch { navigator.backStackEvents.collect { events += it } }
            runCurrent()
            val b = Page(); val c = Other()
            navigator.push { destination = b; onRouteResult<Int> { results += it } }; runCurrent()
            navigator.pop(); runCurrent()
            navigator.push { destination = c }; runCurrent()
            gate.complete(Unit); runCurrent()
            val event = events.filterIsInstance<BackStackEvent.Pop>().single()
            assertSame(b, event.entry)
            assertEquals(listOf(root, c), event.backStack)
            assertTrue(results.isEmpty())
            navigator.pop { greenChannel = true }; runCurrent()
            assertEquals<List<RouteResult<Int>>>(listOf(RouteResult.NoResult), results)
        }
    }

    @Test
    fun missingCallerDoesNotReceiveResultOrRedirectItToAnotherPage() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { navigator, root ->
            val results = mutableListOf<RouteResult<Int>>()
            val b = Page(); val c = Other()
            navigator.push { destination = b; onRouteResult<Int> { results += it } }; runCurrent()
            navigator.pop { top(42) }; runCurrent()
            navigator.push { destination = c }; runCurrent()
            navigator.pop { last(root); greenChannel = true }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(navigator, c)
            assertTrue(results.isEmpty())
        }
    }

    @Test
    fun popCurrentPushInvalidatesOldPopEvenAfterInstanceReenters() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { navigator, root ->
            val b = Page(); val c = Other()
            navigator.push { destination = b }; runCurrent()
            navigator.pop(); runCurrent()
            navigator.push { destination = c; isPopCurrent = true }; runCurrent()
            navigator.push { destination = b }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(navigator, root, c, b)
        }
    }

    @Test
    fun delayedRedirectResolvesNewTargetThenKeepsItBoundAcrossAnotherPush() {
        val redirectGate = CompletableDeferred<Unit>()
        val newTargetGate = CompletableDeferred<Unit>()
        lateinit var c: Other
        var targetChecked: IRouterEntryProvider? = null
        withRouter(interceptor {
            if (request().operation == PopOperation.CURRENT) {
                redirectGate.await()
                redirect { last(c) }
            } else proceed()
        }, mapOf(Other::class to interceptor {
            targetChecked = request().removeTargets.single()
            newTargetGate.await()
            proceed()
        })) { navigator, root ->
            val b = Page(); c = Other(); val d = Page()
            navigator.push { destination = b }; runCurrent()
            navigator.pop(); runCurrent()
            navigator.push { destination = c }; runCurrent()
            redirectGate.complete(Unit); runCurrent()
            assertSame(c, targetChecked)
            navigator.push { destination = d }; runCurrent()
            newTargetGate.complete(Unit); runCurrent()
            assertStack(navigator, root, b, d)
        }
    }

    @Test
    fun expiredSourceRequestCannotRedirectToDeleteAnotherPage() {
        val gate = CompletableDeferred<Unit>()
        lateinit var c: Other
        withRouter(interceptor { gate.await(); redirect { last(c) } }) { navigator, root ->
            val b = Page(); c = Other()
            navigator.push { destination = b }; runCurrent()
            navigator.pop(); runCurrent()
            navigator.push { destination = c }; runCurrent()
            navigator.pop { last(b); greenChannel = true }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(navigator, root, c)
        }
    }

    @Test
    fun directResultWithoutRegisteredConsumerIsDroppedInsteadOfCached() = withRouter { navigator, root ->
        val results = mutableListOf<RouteResult<Any>>()
        navigator.push { destination = Page() }; runCurrent()
        navigator.pop { to(root, 42, "refresh") }; runCurrent()
        navigator.routeResultController.registerConsumer(
            root.contentKey(), buildRouteResultSlotId("refresh", Int::class)
        ) { results += it }
        runCurrent()
        assertStack(navigator, root)
        assertTrue(results.isEmpty())
    }

    @Test
    fun backToWithResultChangesValueAndPreservesReceiverKeyAndType() = withRouter(interceptor {
        proceed(request().withResult(99))
    }) { navigator, root ->
        val results = mutableListOf<RouteResult<Any>>()
        navigator.routeResultController.registerConsumer(
            root.contentKey(), buildRouteResultSlotId("refresh", Int::class)
        ) { results += it }
        navigator.push { destination = Page() }; runCurrent()
        navigator.pop { to(root, 42, "refresh") }; runCurrent()
        assertStack(navigator, root)
        assertEquals<List<RouteResult<Any>>>(listOf(RouteResult.Success(99)), results)
    }

    @Test
    fun withResultRejectsOperationsWithoutAResultReceiverContract() {
        var captured: PopRequest? = null
        withRouter(interceptor { captured = request(); interrupt() }) { navigator, root ->
            val b = Page()
            navigator.push { destination = b }; runCurrent()
            navigator.pop { last(b) }; runCurrent()
            assertFailsWith<IllegalStateException> { requireNotNull(captured).withResult(42) }
            navigator.pop { to(root) }; runCurrent()
            assertFailsWith<IllegalArgumentException> { requireNotNull(captured).withResult(42) }
            assertStack(navigator, root, b)
        }
    }

    @Test
    fun cachedApprovalFromAnotherOperationCannotRemoveCurrentTarget() {
        var cached: PopResponse? = null
        withRouter(interceptor { cached ?: proceed().also { cached = it } }) { navigator, root ->
            navigator.push { destination = Page() }; runCurrent()
            navigator.pop(); runCurrent()
            assertStack(navigator, root)
            val c = Other()
            navigator.push { destination = c }; runCurrent()
            navigator.pop(); runCurrent()
            assertStack(navigator, root, c)
        }
    }

    @Test
    fun modifyingExposedTargetListDoesNotChangeExecutionTargets() {
        var captured: PopRequest? = null
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { captured = request(); gate.await(); proceed() }) { navigator, root ->
            val b = Page(); val c = Other()
            navigator.push { destination = b }; navigator.push { destination = c }; runCurrent()
            navigator.pop { all(Page::class, Other::class) }; runCurrent()
            val copy = requireNotNull(captured).removeTargets as MutableList<IRouterEntryProvider>
            copy.clear()
            assertEquals(listOf(c, b), requireNotNull(captured).removeTargets)
            gate.complete(Unit); runCurrent()
            assertStack(navigator, root)
        }
    }

    @Test
    fun classAndUrlSelectorsStillResolveExpectedInstances() = withRouter { navigator, root ->
        Core.aliasesKV["test://home/page"] = Page::class
        Core.aliasesKV["test://home/root"] = Root::class
        val b = Page(); val c = Other()
        navigator.push { destination = b }; navigator.push { destination = c }; runCurrent()
        navigator.pop { to<Page>() }; runCurrent()
        assertStack(navigator, root, b)
        navigator.pop { last("test://home/page") }; runCurrent()
        assertStack(navigator, root)
        navigator.push { destination = b }; navigator.push { destination = c }; runCurrent()
        navigator.pop { to("test://home/root") }; runCurrent()
        assertStack(navigator, root)
    }

    private fun interceptor(block: suspend IRouterPopInterceptor.Chain.() -> PopResponse) =
        object : IRouterPopInterceptor {
            override suspend fun intercept(chain: IRouterPopInterceptor.Chain) = block(chain)
        }

    private fun assertStack(navigator: Navigator, vararg expected: IRouterEntryProvider) {
        assertEquals(expected.size, navigator.backStack.size)
        expected.forEachIndexed { index, page -> assertSame(page, navigator.backStack[index]) }
    }

    private fun withRouter(
        global: IRouterPopInterceptor? = null,
        pageInterceptors: Map<KClass<*>, IRouterPopInterceptor> = emptyMap(),
        block: suspend TestScope.(Navigator, Root) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val providers = RouterSingleton.providers.toList()
        val routes = Core.masterKV.toMap()
        val aliases = Core.aliasesKV.toMap()
        RouterSingleton.providers.clear()
        Core.masterKV.clear()
        Core.aliasesKV.clear()
        val root = Root()
        val interceptorFailures = mutableListOf<Throwable>()
        fun observeFailures(delegate: IRouterPopInterceptor) = interceptor {
            try {
                delegate.intercept(this)
            } catch (error: Throwable) {
                if (error !is CancellationException) interceptorFailures += error
                throw error
            }
        }
        RouterSingleton.providers += object : ModuleRouter {
            override fun routeMasterKV() = pageInterceptors.mapValues { (_, interceptor) ->
                ParseRouterInfo("", "", "", "", "", emptyList(),
                    listOf(ParsePagePopInterceptorInfo(0, observeFailures(interceptor))), object : IRouterInstance {
                        override fun instance(extras: Map<String, Any>?) = root
                    })
            }
            override fun routeAliasesKV(): Map<String, KClass<*>> = emptyMap()
            override fun globalPushInterceptors(): List<GlobalPushInterceptorInfo> = emptyList()
            override fun globalPopInterceptors() = global?.let { listOf(GlobalPopInterceptorInfo(observeFailures(it), 0)) }.orEmpty()
            override fun entryProviderInstaller(): EntryProviderInstaller = {}
        }
        val navigator = Navigator(root)
        try {
            runCurrent()
            block(navigator, root)
            assertTrue(interceptorFailures.isEmpty(), "Unexpected interceptor failures: $interceptorFailures")
        } finally {
            navigator.core.cancel()
            RouterSingleton.providers.clear()
            RouterSingleton.providers.addAll(providers)
            Core.masterKV.clear(); Core.masterKV.putAll(routes)
            Core.aliasesKV.clear(); Core.aliasesKV.putAll(aliases)
            Dispatchers.resetMain()
        }
    }
}
