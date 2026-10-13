package io.github.limuyang2.router

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultStoreTest {
    @Test
    fun directResultWaitsForDisposedConsumerToRebindAndIsConsumedOnce() {
        val store = ResultStore()
        val oldResults = mutableListOf<RouteResult<Any>>()
        val newResults = mutableListOf<RouteResult<Any>>()
        store.bindConsumerSlot("A", "slot") { oldResults += it }
        store.unbindConsumerSlot("A", "slot")

        store.queueDirectResult("A", "slot", RouteResult.Success(42))
        assertTrue(store.drainDeliverableResults("A").isEmpty())
        assertTrue(oldResults.isEmpty())

        store.bindConsumerSlot("A", "slot") { newResults += it }
        store.drainDeliverableResults("A").forEach { it() }
        assertEquals<List<RouteResult<Any>>>(listOf(RouteResult.Success(42)), newResults)
        assertTrue(oldResults.isEmpty())
        store.unbindConsumerSlot("A", "slot")
        store.bindConsumerSlot("A", "slot") { newResults += it }
        assertTrue(store.drainDeliverableResults("A").isEmpty())
    }

    @Test
    fun removingOwnerDropsQueuedResultsAndItsSlotDeclarations() {
        val store = ResultStore()
        val results = mutableListOf<RouteResult<Any>>()
        store.bindConsumerSlot("A", "slot") { results += it }
        store.unbindConsumerSlot("A", "slot")
        store.queueDirectResult("A", "slot", RouteResult.Success(42))
        store.dropOwnerState("A")

        // Reusing the content key must not revive either the pending result or the old slot.
        store.queueDirectResult("A", "slot", RouteResult.Success(99))
        store.bindConsumerSlot("A", "slot") { results += it }
        assertTrue(store.drainDeliverableResults("A").isEmpty())
        assertTrue(results.isEmpty())
    }
}
