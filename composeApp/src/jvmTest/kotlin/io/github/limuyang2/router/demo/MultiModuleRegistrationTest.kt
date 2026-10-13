package io.github.limuyang2.router.demo

import io.github.limuyang2.router.Navigator
import io.github.limuyang2.router.RouteResult
import io.github.limuyang2.router.demo.feature.account.AccountPage
import io.github.limuyang2.router.demo.feature.product.ProductPage
import io.github.limuyang2.router.demo.feature.shared.HelpPage
import io.github.limuyang2.router.demo.feature.shared.HelpResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/** Uses the actual generated host registry and the three separately compiled feature modules. */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiModuleRegistrationTest {
    @Test
    fun aggregatesDiamondDependencyOnceAndResolvesAllFeatureRoutes() = withNavigator { navigator ->
        assertEquals(ProductPage::class, navigator.findClassByUrl("test://modules/product?id=42"))
        assertEquals(AccountPage::class, navigator.findClassByUrl("test://modules/account"))
        assertEquals(HelpPage::class, navigator.findClassByUrl("test://modules/help"))
        // One installer per logical module: host, product, account and shared help.
        assertEquals(4, navigator.pageProviderInstaller.size)
        initDemoRouter()
        assertEquals(4, navigator.pageProviderInstaller.size)
    }

    @Test
    fun productUrlInjectsParametersAndSharedPageReturnsTypedResultOnce() = withNavigator { navigator ->
        navigator.push("test://modules/product?id=42")
        runCurrent()
        val product = assertIs<ProductPage>(navigator.last())
        assertEquals(42, product.id)
        val results = mutableListOf<RouteResult<HelpResult>>()
        navigator.pushForResult<HelpResult>(HelpPage("商品 42")) { results += it }
        runCurrent()
        val response = HelpResult("商品 42", "已解决")
        navigator.pop(response)
        runCurrent()
        assertSame(product, navigator.last())
        assertEquals(1, results.size)
        assertEquals(response, assertIs<RouteResult.Success<HelpResult>>(results.single()).value)
        assertEquals(2, navigator.backStack.size)
    }

    @Test
    fun accountInstanceOpensSharedUrlAndReceivesItsResult() = withNavigator { navigator ->
        val account = AccountPage("小明")
        navigator.push(account)
        runCurrent()
        val results = mutableListOf<RouteResult<HelpResult>>()
        navigator.pushForResult<HelpResult>("test://modules/help?topic=account") { results += it }
        runCurrent()
        assertEquals("account", assertIs<HelpPage>(navigator.last()).topic)
        val response = HelpResult("account", "已解决")
        navigator.pop(response)
        runCurrent()
        assertSame(account, navigator.last())
        assertEquals(1, results.size)
        assertEquals(response, assertIs<RouteResult.Success<HelpResult>>(results.single()).value)
    }

    private fun withNavigator(block: suspend TestScope.(Navigator) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            // The demo's generated providers are idempotent and are the only providers in this test JVM.
            initDemoRouter()
            val navigator = Navigator(Router.Home())
            navigator.backStack // Initialize the lazy runtime on the controlled dispatcher.
            runCurrent()
            block(navigator)
            runCurrent()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
