package io.github.limuyang2.router

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class PushInstanceUniquenessTest {
    private data class Page(val id: Int) : IRouterEntryProvider
    private object SingletonPage : IRouterEntryProvider

    @Test
    fun rejectsInstanceAlreadyBelowTopWithFailureCallbacks() = withCore { navigator ->
        val root = navigator.last()!!
        navigator.push { destination = Page(1) }
        runCurrent()
        val before = navigator.backStack.toList()
        val callbacks = mutableListOf<String>()

        navigator.push {
            destination = root
            routeBeforeCallback = { callbacks += "before" }
            routeSuccessCallback = { callbacks += "success" }
            routeFailureCallback = { callbacks += "failure" }
            routeCompletedCallback = { callbacks += "completed" }
        }
        runCurrent()

        assertEquals(before, navigator.backStack.toList())
        assertEquals(listOf("failure", "completed"), callbacks)
    }

    @Test
    fun queuedPushesOfSingletonAddOnlyOneEntry() = withCore { navigator ->
        repeat(2) { navigator.push { destination = SingletonPage } }
        runCurrent()

        assertEquals(2, navigator.backStack.size)
        assertSame(SingletonPage, navigator.last())
    }

    @Test
    fun allowsEqualButDistinctInstances() = withCore { navigator ->
        val first = Page(1)
        val second = Page(1)
        assertEquals(first, second)
        assertEquals(first.contentKey(), second.contentKey())
        assertNotSame(first, second)

        navigator.push { destination = first }
        navigator.push { destination = second }
        runCurrent()

        assertEquals(3, navigator.backStack.size)
        assertSame(first, navigator.backStack[1])
        assertSame(second, navigator.backStack[2])
    }

    @Test
    fun allowsInstanceToReenterAfterRemoval() = withCore { navigator ->
        navigator.push { destination = SingletonPage }
        runCurrent()
        navigator.pop()
        runCurrent()
        assertEquals(1, navigator.backStack.size)

        navigator.push { destination = SingletonPage }
        runCurrent()

        assertEquals(2, navigator.backStack.size)
        assertSame(SingletonPage, navigator.last())
    }

    @Test
    fun navigationFlagsDoNotAllowDuplicateInstances() = withCore { navigator ->
        val root = navigator.last()!!
        val top = Page(1)
        navigator.push { destination = top }
        runCurrent()
        var failures = 0

        repeat(3) { mode ->
            navigator.push {
                destination = root
                greenChannel = mode == 0
                isCleanStack = mode == 1
                isPopCurrent = mode == 2
                routeFailureCallback = { failures++ }
            }
            runCurrent()
            assertEquals(2, navigator.backStack.size)
            assertSame(root, navigator.backStack[0])
            assertSame(top, navigator.last())
        }
        assertEquals(3, failures)
    }

    @Test
    fun preventDuplicatesStillRejectsSameTopTypeWithDifferentInstance() = withCore { navigator ->
        val root = navigator.last()!!
        navigator.push {
            destination = Page(1)
            preventDuplicates = true
        }
        runCurrent()

        assertEquals(1, navigator.backStack.size)
        assertSame(root, navigator.last())
    }

    private fun withCore(block: suspend TestScope.(Navigator) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val navigator = Navigator(Page(0))
        try {
            runCurrent()
            block(navigator)
        } finally {
            navigator.core.cancel()
            Dispatchers.resetMain()
        }
    }
}
