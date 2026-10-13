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
    fun rejectsInstanceAlreadyBelowTopWithFailureCallbacks() = withCore { core ->
        val root = core.getTop()!!
        core.push { destination = Page(1) }
        runCurrent()
        val before = core.mBackStack.toList()
        val callbacks = mutableListOf<String>()

        core.push {
            destination = root
            routeBeforeCallback = { callbacks += "before" }
            routeSuccessCallback = { callbacks += "success" }
            routeFailureCallback = { callbacks += "failure" }
            routeCompletedCallback = { callbacks += "completed" }
        }
        runCurrent()

        assertEquals(before, core.mBackStack.toList())
        assertEquals(listOf("failure", "completed"), callbacks)
    }

    @Test
    fun queuedPushesOfSingletonAddOnlyOneEntry() = withCore { core ->
        repeat(2) { core.push { destination = SingletonPage } }
        runCurrent()

        assertEquals(2, core.mBackStack.size)
        assertSame(SingletonPage, core.getTop())
    }

    @Test
    fun allowsEqualButDistinctInstances() = withCore { core ->
        val first = Page(1)
        val second = Page(1)
        assertEquals(first, second)
        assertEquals(first.contentKey(), second.contentKey())
        assertNotSame(first, second)

        core.push { destination = first }
        core.push { destination = second }
        runCurrent()

        assertEquals(3, core.mBackStack.size)
        assertSame(first, core.mBackStack[1])
        assertSame(second, core.mBackStack[2])
    }

    @Test
    fun allowsInstanceToReenterAfterRemoval() = withCore { core ->
        core.push { destination = SingletonPage }
        runCurrent()
        core.pop()
        runCurrent()
        assertEquals(1, core.mBackStack.size)

        core.push { destination = SingletonPage }
        runCurrent()

        assertEquals(2, core.mBackStack.size)
        assertSame(SingletonPage, core.getTop())
    }

    @Test
    fun navigationFlagsDoNotAllowDuplicateInstances() = withCore { core ->
        val root = core.getTop()!!
        val top = Page(1)
        core.push { destination = top }
        runCurrent()
        var failures = 0

        repeat(3) { mode ->
            core.push {
                destination = root
                greenChannel = mode == 0
                isCleanStack = mode == 1
                isPopCurrent = mode == 2
                routeFailureCallback = { failures++ }
            }
            runCurrent()
            assertEquals(2, core.mBackStack.size)
            assertSame(root, core.mBackStack[0])
            assertSame(top, core.getTop())
        }
        assertEquals(3, failures)
    }

    @Test
    fun preventDuplicatesStillRejectsSameTopTypeWithDifferentInstance() = withCore { core ->
        val root = core.getTop()!!
        core.push {
            destination = Page(1)
            preventDuplicates = true
        }
        runCurrent()

        assertEquals(1, core.mBackStack.size)
        assertSame(root, core.getTop())
    }

    private fun withCore(block: suspend TestScope.(Core) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val core = Core(Page(0))
        try {
            runCurrent()
            block(core)
        } finally {
            core.cancel()
            Dispatchers.resetMain()
        }
    }
}
