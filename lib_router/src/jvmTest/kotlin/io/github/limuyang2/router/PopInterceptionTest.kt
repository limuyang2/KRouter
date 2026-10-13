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
        withRouter(interceptor) { core, root ->
            val b = Page()
            val c = Other()
            val results = mutableListOf<RouteResult<Int>>()
            core.push { destination = b; onRouteResult<Int> { results += it } }
            runCurrent()
            core.pop { top(42) }
            runCurrent()
            val captured = requireNotNull(request)
            assertEquals(PopOperation.CURRENT, captured.operation)
            assertSame(b, captured.removeTargets.single())
            assertTrue(captured.hasResult)
            assertEquals(42, captured.result)
            core.push { destination = c }
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            assertStack(core, root, c)
            assertTrue(results.isEmpty())
            core.pop { greenChannel = true }
            runCurrent()
            assertEquals<List<RouteResult<Int>>>(listOf(RouteResult.Success(42)), results)
        }
    }

    @Test
    fun interruptedPopLeavesOriginalAndNewPageUntouched() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); interrupt() }) { core, root ->
            val b = Page(); val c = Other()
            core.push { destination = b }; runCurrent()
            core.pop(); runCurrent()
            core.push { destination = c }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(core, root, b, c)
        }
    }

    @Test
    fun duplicatePendingPopsRemoveTheirTargetOnlyOnce() {
        val gate = CompletableDeferred<Unit>()
        var interceptions = 0
        withRouter(interceptor { interceptions++; gate.await(); proceed() }) { core, root ->
            core.push { destination = Page() }; runCurrent()
            core.pop(); core.pop(); runCurrent()
            val c = Other()
            core.push { destination = c }; runCurrent()
            assertEquals(2, interceptions)
            gate.complete(Unit); runCurrent()
            assertStack(core, root, c)
        }
    }

    @Test
    fun removedThenReinsertedInstanceIsNotDeletedByOldResponse() {
        val gate = CompletableDeferred<Unit>()
        // Even a response already approved by the tail of the chain must be revalidated at commit.
        withRouter(interceptor { val response = proceed(); gate.await(); response }) { core, root ->
            val b = Page()
            core.push { destination = b }; runCurrent()
            core.pop(); runCurrent()
            core.pop { greenChannel = true }; runCurrent()
            core.push { destination = b }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(core, root, b)
        }
    }

    @Test
    fun cleanStackInvalidatesPendingPop() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { core, _ ->
            val b = Page(); val c = Other()
            core.push { destination = b }; runCurrent()
            core.pop(); runCurrent()
            core.push { destination = c; isCleanStack = true }; runCurrent()
            core.push { destination = b }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(core, c, b)
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
            Other::class to interceptor { calls += "C"; proceed() })) { core, root ->
            b = Page(); val c = Other()
            core.push { destination = b }; core.push { destination = c }; runCurrent()
            core.pop(); runCurrent()
            assertEquals(listOf("global:CURRENT", "global:REMOVE", "B"), calls)
            assertStack(core, root, b, c)
        }
    }

    @Test
    fun redirectFromPageInterceptorCanRemoveDifferentPage() {
        lateinit var b: Page
        var bChecked = false
        withRouter(pageInterceptors = mapOf(
            Other::class to interceptor { redirect { last(b) } },
            Page::class to interceptor { bChecked = true; assertSame(b, request().removeTargets.single()); proceed() },
        )) { core, root ->
            b = Page(); val c = Other()
            core.push { destination = b }; core.push { destination = c }; runCurrent()
            core.pop(); runCurrent()
            assertTrue(bChecked)
            assertStack(core, root, c)
        }
    }

    @Test
    fun unchangedRedirectKeepsBoundTargetAndDoesNotRestartChain() {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        withRouter(interceptor { calls++; gate.await(); redirect { top(9) } }) { core, root ->
            val results = mutableListOf<RouteResult<Int>>()
            val c = Other()
            core.push { destination = Page(); onRouteResult<Int> { results += it } }; runCurrent()
            core.pop(); runCurrent()
            core.push { destination = c }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertEquals(1, calls)
            assertStack(core, root, c)
            core.pop { greenChannel = true }; runCurrent()
            assertEquals<List<RouteResult<Int>>>(listOf(RouteResult.Success(9)), results)
        }
    }

    @Test
    fun withResultPreservesTargetAndDistinguishesNullFromNoResult() {
        var requestHasResult = true
        withRouter(interceptor {
            requestHasResult = request().hasResult
            proceed(request().withResult(null))
        }) { core, _ ->
            val results = mutableListOf<RouteResult<Int>>()
            core.push { destination = Page(); onRouteResult<Int> { results += it } }; runCurrent()
            core.pop(); runCurrent()
            assertFalse(requestHasResult)
            assertEquals<List<RouteResult<Int>>>(listOf(RouteResult.Success<Int>(null)), results)
        }
    }

    @Test
    fun changedResultStillUsesOriginalExpectedType() = withRouter(interceptor {
        proceed(request().withResult("wrong type"))
    }) { core, _ ->
        val results = mutableListOf<RouteResult<Int>>()
        core.push { destination = Page(); onRouteResult<Int> { results += it } }; runCurrent()
        core.pop { top(1) }; runCurrent()
        assertTrue(results.single() is RouteResult.TypeMismatch)
    }

    @Test
    fun redirectLoopStopsWithoutMutatingStack() {
        lateinit var b: Page
        var calls = 0
        withRouter(interceptor {
            calls++
            if (request().operation == PopOperation.CURRENT) redirect { last(b) } else redirect { top() }
        }) { core, root ->
            b = Page()
            core.push { destination = b }; runCurrent()
            core.pop(); runCurrent()
            assertTrue(calls in 2..17)
            assertStack(core, root, b)
        }
    }

    @Test
    fun redirectToMissingTargetDoesNotFallBackToCurrent() = withRouter(interceptor {
        redirect { last(Page()) }
    }) { core, root ->
        val b = Page()
        core.push { destination = b }; runCurrent()
        core.pop(); runCurrent()
        assertStack(core, root, b)
    }

    @Test
    fun removeAllBindsOnlyInitiallyMatchedPagesAndExcludesRootFromInterception() {
        val gate = CompletableDeferred<Unit>()
        var seen: List<IRouterEntryProvider> = emptyList()
        withRouter(interceptor { seen = request().removeTargets; gate.await(); proceed() }) { core, root ->
            val b = Page(); val c = Other(); val d = Page()
            core.push { destination = b }; core.push { destination = c }; runCurrent()
            core.pop { all(Root::class, Page::class, Other::class) }; runCurrent()
            assertEquals(listOf(c, b), seen)
            core.push { destination = d }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(core, root, d)
        }
    }

    @Test
    fun removingOneMemberInvalidatesWholePendingBatch() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { core, root ->
            val b = Page(); val c = Other()
            core.push { destination = b }; core.push { destination = c }; runCurrent()
            core.pop { all(Page::class, Other::class) }; runCurrent()
            core.pop { last(b); greenChannel = true }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(core, root, c)
        }
    }

    @Test
    fun commitRetainsCurrentRootIfOriginalRootWasRemovedWhileWaiting() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { core, root ->
            val b = Page(); val c = Other()
            core.push { destination = b }; core.push { destination = c }; runCurrent()
            core.pop { all(Page::class, Other::class) }; runCurrent()
            core.pop { last(root); greenChannel = true }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(core, b)
        }
    }

    @Test
    fun backToUsesOriginalReceiverAndDefersDirectResultWhileNewPageIsOnTop() {
        val gate = CompletableDeferred<Unit>()
        var request: PopRequest? = null
        withRouter(interceptor { request = request(); gate.await(); proceed() }) { core, root ->
            val results = mutableListOf<RouteResult<Any>>()
            val slot = buildRouteResultSlotId("refresh", Int::class)
            core.routeResultController.registerConsumer(root.contentKey(), slot) { results += it }
            val b = Page(); val c = Other(); val d = Page()
            core.push { destination = b }; core.push { destination = c }; runCurrent()
            core.pop { to(root, 42, "refresh") }; runCurrent()
            assertSame(root, request!!.backToPage)
            assertEquals(listOf(c, b), request!!.removeTargets)
            core.push { destination = d }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(core, root, d)
            assertTrue(results.isEmpty())
            // Registration while the owner is still behind D must not consume its inbox.
            core.routeResultController.unregisterConsumer(root.contentKey(), slot)
            core.routeResultController.registerConsumer(root.contentKey(), slot) { results += it }
            runCurrent()
            assertTrue(results.isEmpty())
            core.pop { greenChannel = true }; runCurrent()
            assertEquals<List<RouteResult<Any>>>(listOf(RouteResult.Success(42)), results)
        }
    }

    @Test
    fun removingBackToReceiverInvalidatesPendingRequest() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { core, root ->
            val b = Page(); val c = Other()
            core.push { destination = b }; core.push { destination = c }; runCurrent()
            core.pop { to(root) }; runCurrent()
            core.pop { last(root); greenChannel = true }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(core, b, c)
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
        }) { core, root ->
            first = EqualPage(1); second = EqualPage(1)
            core.push { destination = first }; core.push { destination = second }; runCurrent()
            core.pop { last(first) }; runCurrent()
            assertEquals(2, calls)
            assertStack(core, root, first)
        }
    }

    @Test
    fun backToInstanceKeepsExactEqualInstance() = withRouter { core, root ->
        val first = EqualPage(1); val second = EqualPage(1)
        core.push { destination = first }; core.push { destination = second }; runCurrent()
        core.pop { to(first) }; runCurrent()
        assertStack(core, root, first)
    }

    @Test
    fun noResultAndPopEventReferToOriginalPageAfterNewPush() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { core, root ->
            val results = mutableListOf<RouteResult<Int>>()
            val events = mutableListOf<BackStackEvent>()
            backgroundScope.launch { core.backStackEvents.collect { events += it } }
            runCurrent()
            val b = Page(); val c = Other()
            core.push { destination = b; onRouteResult<Int> { results += it } }; runCurrent()
            core.pop(); runCurrent()
            core.push { destination = c }; runCurrent()
            gate.complete(Unit); runCurrent()
            val event = events.filterIsInstance<BackStackEvent.Pop>().single()
            assertSame(b, event.entry)
            assertEquals(listOf(root, c), event.backStack)
            assertTrue(results.isEmpty())
            core.pop { greenChannel = true }; runCurrent()
            assertEquals<List<RouteResult<Int>>>(listOf(RouteResult.NoResult), results)
        }
    }

    @Test
    fun missingCallerDoesNotReceiveResultOrRedirectItToAnotherPage() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { core, root ->
            val results = mutableListOf<RouteResult<Int>>()
            val b = Page(); val c = Other()
            core.push { destination = b; onRouteResult<Int> { results += it } }; runCurrent()
            core.pop { top(42) }; runCurrent()
            core.push { destination = c }; runCurrent()
            core.pop { last(root); greenChannel = true }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(core, c)
            assertTrue(results.isEmpty())
        }
    }

    @Test
    fun popCurrentPushInvalidatesOldPopEvenAfterInstanceReenters() {
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { gate.await(); proceed() }) { core, root ->
            val b = Page(); val c = Other()
            core.push { destination = b }; runCurrent()
            core.pop(); runCurrent()
            core.push { destination = c; isPopCurrent = true }; runCurrent()
            core.push { destination = b }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(core, root, c, b)
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
        })) { core, root ->
            val b = Page(); c = Other(); val d = Page()
            core.push { destination = b }; runCurrent()
            core.pop(); runCurrent()
            core.push { destination = c }; runCurrent()
            redirectGate.complete(Unit); runCurrent()
            assertSame(c, targetChecked)
            core.push { destination = d }; runCurrent()
            newTargetGate.complete(Unit); runCurrent()
            assertStack(core, root, b, d)
        }
    }

    @Test
    fun expiredSourceRequestCannotRedirectToDeleteAnotherPage() {
        val gate = CompletableDeferred<Unit>()
        lateinit var c: Other
        withRouter(interceptor { gate.await(); redirect { last(c) } }) { core, root ->
            val b = Page(); c = Other()
            core.push { destination = b }; runCurrent()
            core.pop(); runCurrent()
            core.push { destination = c }; runCurrent()
            core.pop { last(b); greenChannel = true }; runCurrent()
            gate.complete(Unit); runCurrent()
            assertStack(core, root, c)
        }
    }

    @Test
    fun directResultWithoutRegisteredConsumerIsDroppedInsteadOfCached() = withRouter { core, root ->
        val results = mutableListOf<RouteResult<Any>>()
        core.push { destination = Page() }; runCurrent()
        core.pop { to(root, 42, "refresh") }; runCurrent()
        core.routeResultController.registerConsumer(
            root.contentKey(), buildRouteResultSlotId("refresh", Int::class)
        ) { results += it }
        runCurrent()
        assertStack(core, root)
        assertTrue(results.isEmpty())
    }

    @Test
    fun backToWithResultChangesValueAndPreservesReceiverKeyAndType() = withRouter(interceptor {
        proceed(request().withResult(99))
    }) { core, root ->
        val results = mutableListOf<RouteResult<Any>>()
        core.routeResultController.registerConsumer(
            root.contentKey(), buildRouteResultSlotId("refresh", Int::class)
        ) { results += it }
        core.push { destination = Page() }; runCurrent()
        core.pop { to(root, 42, "refresh") }; runCurrent()
        assertStack(core, root)
        assertEquals<List<RouteResult<Any>>>(listOf(RouteResult.Success(99)), results)
    }

    @Test
    fun withResultRejectsOperationsWithoutAResultReceiverContract() {
        var captured: PopRequest? = null
        withRouter(interceptor { captured = request(); interrupt() }) { core, root ->
            val b = Page()
            core.push { destination = b }; runCurrent()
            core.pop { last(b) }; runCurrent()
            assertFailsWith<IllegalStateException> { requireNotNull(captured).withResult(42) }
            core.pop { to(root) }; runCurrent()
            assertFailsWith<IllegalArgumentException> { requireNotNull(captured).withResult(42) }
            assertStack(core, root, b)
        }
    }

    @Test
    fun cachedApprovalFromAnotherOperationCannotRemoveCurrentTarget() {
        var cached: PopResponse? = null
        withRouter(interceptor { cached ?: proceed().also { cached = it } }) { core, root ->
            core.push { destination = Page() }; runCurrent()
            core.pop(); runCurrent()
            assertStack(core, root)
            val c = Other()
            core.push { destination = c }; runCurrent()
            core.pop(); runCurrent()
            assertStack(core, root, c)
        }
    }

    @Test
    fun modifyingExposedTargetListDoesNotChangeExecutionTargets() {
        var captured: PopRequest? = null
        val gate = CompletableDeferred<Unit>()
        withRouter(interceptor { captured = request(); gate.await(); proceed() }) { core, root ->
            val b = Page(); val c = Other()
            core.push { destination = b }; core.push { destination = c }; runCurrent()
            core.pop { all(Page::class, Other::class) }; runCurrent()
            val copy = requireNotNull(captured).removeTargets as MutableList<IRouterEntryProvider>
            copy.clear()
            assertEquals(listOf(c, b), requireNotNull(captured).removeTargets)
            gate.complete(Unit); runCurrent()
            assertStack(core, root)
        }
    }

    @Test
    fun classAndUrlSelectorsStillResolveExpectedInstances() = withRouter { core, root ->
        Core.aliasesKV["test://home/page"] = Page::class
        Core.aliasesKV["test://home/root"] = Root::class
        val b = Page(); val c = Other()
        core.push { destination = b }; core.push { destination = c }; runCurrent()
        core.pop { to<Page>() }; runCurrent()
        assertStack(core, root, b)
        core.pop { last("test://home/page") }; runCurrent()
        assertStack(core, root)
        core.push { destination = b }; core.push { destination = c }; runCurrent()
        core.pop { to("test://home/root") }; runCurrent()
        assertStack(core, root)
    }

    private fun interceptor(block: suspend IRouterPopInterceptor.Chain.() -> PopResponse) =
        object : IRouterPopInterceptor {
            override suspend fun intercept(chain: IRouterPopInterceptor.Chain) = block(chain)
        }

    private fun assertStack(core: Core, vararg expected: IRouterEntryProvider) {
        assertEquals(expected.size, core.mBackStack.size)
        expected.forEachIndexed { index, page -> assertSame(page, core.mBackStack[index]) }
    }

    private fun withRouter(
        global: IRouterPopInterceptor? = null,
        pageInterceptors: Map<KClass<*>, IRouterPopInterceptor> = emptyMap(),
        block: suspend TestScope.(Core, Root) -> Unit,
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
        val core = Core(root)
        try {
            runCurrent()
            block(core, root)
            assertTrue(interceptorFailures.isEmpty(), "Unexpected interceptor failures: $interceptorFailures")
        } finally {
            core.cancel()
            RouterSingleton.providers.clear()
            RouterSingleton.providers.addAll(providers)
            Core.masterKV.clear(); Core.masterKV.putAll(routes)
            Core.aliasesKV.clear(); Core.aliasesKV.putAll(aliases)
            Dispatchers.resetMain()
        }
    }
}
