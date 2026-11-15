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
        val navigator = Navigator(root)
        try {
            runCurrent()
            repeat(2) { navigator.push { destination = Page() } }
            runCurrent()
            navigator.pop { all(Page::class) }
            runCurrent()
            assertEquals(1, navigator.backStack.size)
            assertSame(root, navigator.last())
        } finally {
            navigator.core.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun removingAllTypesThroughGreenChannelRetainsOriginalRoot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val root = Page()
        val navigator = Navigator(root)
        try {
            runCurrent()
            navigator.push { destination = OtherPage() }
            runCurrent()
            navigator.pop {
                all(Page::class, OtherPage::class)
                greenChannel = true
            }
            runCurrent()
            assertEquals(1, navigator.backStack.size)
            assertSame(root, navigator.last())
        } finally {
            navigator.core.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun removingOnlyRootIsAllowedWhenAnotherPageRemains() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val navigator = Navigator(Page())
        val other = OtherPage()
        try {
            runCurrent()
            navigator.push { destination = other }
            runCurrent()
            navigator.pop { all(Page::class) }
            runCurrent()
            assertEquals(1, navigator.backStack.size)
            assertSame(other, navigator.last())
        } finally {
            navigator.core.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun removingSingletonRootDoesNothing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val root = Page()
        val navigator = Navigator(root)
        try {
            runCurrent()
            navigator.pop { all(Page::class) }
            runCurrent()
            assertEquals(1, navigator.backStack.size)
            assertSame(root, navigator.last())
        } finally {
            navigator.core.cancel()
            Dispatchers.resetMain()
        }
    }
}
