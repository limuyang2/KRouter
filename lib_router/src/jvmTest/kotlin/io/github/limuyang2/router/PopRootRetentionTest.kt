package io.github.limuyang2.router

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class PopRootRetentionTest {
    private class Page : IRouterEntryProvider
    private class OtherPage : IRouterEntryProvider

    @Test
    fun removingAllMatchingPagesRetainsOriginalRoot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val root = Page()
        val core = Core(root)
        try {
            runCurrent()
            repeat(2) { core.push { destination = Page() } }
            runCurrent()
            core.pop { all(Page::class) }
            runCurrent()
            assertEquals(1, core.mBackStack.size)
            assertSame(root, core.getTop())
        } finally {
            core.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun removingAllTypesThroughGreenChannelRetainsOriginalRoot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val root = Page()
        val core = Core(root)
        try {
            runCurrent()
            core.push { destination = OtherPage() }
            runCurrent()
            core.pop {
                all(Page::class, OtherPage::class)
                greenChannel = true
            }
            runCurrent()
            assertEquals(1, core.mBackStack.size)
            assertSame(root, core.getTop())
        } finally {
            core.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun removingOnlyRootIsAllowedWhenAnotherPageRemains() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val core = Core(Page())
        val other = OtherPage()
        try {
            runCurrent()
            core.push { destination = other }
            runCurrent()
            core.pop { all(Page::class) }
            runCurrent()
            assertEquals(1, core.mBackStack.size)
            assertSame(other, core.getTop())
        } finally {
            core.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun removingSingletonRootDoesNothing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val root = Page()
        val core = Core(root)
        try {
            runCurrent()
            core.pop { all(Page::class) }
            runCurrent()
            assertEquals(1, core.mBackStack.size)
            assertSame(root, core.getTop())
        } finally {
            core.cancel()
            Dispatchers.resetMain()
        }
    }
}
