package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.limuyang2.router.EntryProviderInstaller
import io.github.limuyang2.router.GlobalPopInterceptorInfo
import io.github.limuyang2.router.GlobalPushInterceptorInfo
import io.github.limuyang2.router.IRouterEntryProvider
import io.github.limuyang2.router.IRouterPopInterceptor
import io.github.limuyang2.router.ModuleRouter
import io.github.limuyang2.router.PopOperation
import io.github.limuyang2.router.PopRequest
import io.github.limuyang2.router.PopResponse
import io.github.limuyang2.router.RouteResult
import io.github.limuyang2.router.info.ParseRouterInfo
import io.github.limuyang2.router.interrupt
import io.github.limuyang2.router.registerModuleRouters
import io.github.limuyang2.router.rememberRouteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.reflect.KClass

/** Runs through the demo's real NavDisplay, decorators, animations and Android back dispatch. */
@RunWith(AndroidJUnit4::class)
class PopNavigationDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    // Register before MainActivity initializes the demo Navigator and snapshots its interceptors.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                registerModuleRouters(DeviceTestModule)
            }
        }
    }).around(compose)

    private lateinit var a: DevicePage
    private val gates = mutableListOf<CompletableDeferred<Unit>>()

    @Before
    fun showRoot() {
        compose.runOnIdle {
            DeviceTestModule.handler = null
            DeviceTestModule.failures.clear()
            a = DevicePage("A")
            navigator.push {
                destination = a
                isCleanStack = true
                greenChannel = true
            }
        }
        assertStack(a)
        show("Page A")
    }

    @After
    fun cleanUp() {
        compose.runOnIdle {
            gates.forEach { it.cancel() }
            DeviceTestModule.handler = null
            navigator.push {
                destination = Router.Home()
                isCleanStack = true
                greenChannel = true
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Unexpected interceptor failures: ${DeviceTestModule.failures}",
                DeviceTestModule.failures.isEmpty())
        }
    }

    @Test
    fun pendingPopKeepsNewPageAndDeliversRewrittenResultOnceAfterRecreation() {
        val b = DevicePage("B")
        val c = DevicePage("C")
        compose.runOnIdle {
            a.next = b
            b.next = c
        }
        click("Open B")
        assertStack(a, b)
        val gate = gate()
        var captured: PopRequest? = null
        intercept {
            if (request().removeTargets.singleOrNull() === b) {
                captured = request()
                gate.await()
                proceed(request().withResult("rewritten"))
            } else proceed()
        }
        click("Return B")
        await { captured != null }
        compose.runOnIdle {
            val request = requireNotNull(captured)
            assertSame(b, request.removeTargets.single())
            assertEquals(PopOperation.CURRENT, request.operation)
            assertEquals("from-B", request.result)
        }
        click("Open C")
        assertStack(a, b, c)
        // Stop/start the actual Activity while the coroutine is suspended.
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.activityRule.scenario.recreate()
        show("Page C")
        compose.runOnIdle { gate.complete(Unit) }
        assertStack(a, c)
        show("Page C")
        compose.runOnIdle { assertTrue(a.results.isEmpty()) }
        pressBack()
        assertStack(a)
        show("A result: rewritten")
        show("A deliveries: 1")
        compose.activityRule.scenario.recreate()
        show("A deliveries: 1")
        compose.runOnIdle { assertEquals(listOf(RouteResult.Success("rewritten")), a.results) }
    }

    @Test
    fun rejectingPendingPopPreservesOriginalAndNewPage() {
        val b = DevicePage("B")
        val c = DevicePage("C")
        push(b)
        val gate = gate()
        var waiting = false
        intercept {
            if (request().removeTargets.singleOrNull() === b) {
                waiting = true
                gate.await()
                interrupt()
            } else proceed()
        }
        pressBack()
        await { waiting }
        push(c)
        compose.runOnIdle { gate.complete(Unit) }
        assertStack(a, b, c)
        show("Page C")
        pressBack()
        assertStack(a, b)
        show("Page B")
    }

    @Test
    fun delayedRedirectRemovesNewlySelectedInstanceAndKeepsForegroundPage() {
        val b = DevicePage("B")
        val c = DevicePage("C")
        val d = DevicePage("D")
        push(b)
        push(c)
        val gate = gate()
        val selected = mutableListOf<IRouterEntryProvider>()
        intercept {
            selected += request().removeTargets.single()
            if (request().operation == PopOperation.CURRENT) {
                gate.await()
                redirect { last(b) }
            } else proceed()
        }
        pressBack()
        await { selected.isNotEmpty() }
        push(d)
        compose.runOnIdle { gate.complete(Unit) }
        assertStack(a, c, d)
        show("Page D")
        compose.runOnIdle {
            assertEquals(2, selected.size)
            assertSame(c, selected[0])
            assertSame(b, selected[1])
        }
    }

    @Test
    fun pendingBatchRemovalRetainsRootAndNewPage() {
        val b = DevicePage("B")
        val c = DevicePage("C")
        val d = DevicePage("D")
        push(b)
        push(c)
        val gate = gate()
        var captured: PopRequest? = null
        intercept { captured = request(); gate.await(); proceed() }
        compose.runOnIdle { navigator.pop { all(DevicePage::class) } }
        await { captured != null }
        compose.runOnIdle { assertEquals(listOf(c, b), captured!!.removeTargets) }
        push(d)
        compose.runOnIdle { gate.complete(Unit) }
        assertStack(a, d)
        show("Page D")
        intercept { proceed() }
        compose.runOnIdle { navigator.pop { all(DevicePage::class) } }
        assertStack(a)
        show("Page A")
    }

    @Test
    fun pendingBackToDeliversToOriginalConsumerOnlyWhenItReturns() {
        val b = DevicePage("B")
        val c = DevicePage("C")
        val d = DevicePage("D")
        push(b)
        push(c)
        val gate = gate()
        var captured: PopRequest? = null
        intercept {
            if (request().operation == PopOperation.BACK_TO) {
                captured = request()
                gate.await()
            }
            proceed()
        }
        compose.runOnIdle {
            navigator.pop { to(a, "back-to-A", key = "device-result") }
        }
        await { captured != null }
        compose.runOnIdle { assertSame(a, captured!!.backToPage) }
        push(d)
        compose.runOnIdle { gate.complete(Unit) }
        assertStack(a, d)
        compose.activityRule.scenario.recreate()
        show("Page D")
        compose.runOnIdle { assertTrue(a.results.isEmpty()) }
        pressBack()
        assertStack(a)
        show("A result: back-to-A")
        show("A deliveries: 1")
        compose.activityRule.scenario.recreate()
        show("A deliveries: 1")
        compose.runOnIdle { assertEquals(listOf(RouteResult.Success("back-to-A")), a.results) }
    }

    @Test
    fun duplicateInstanceIsRejectedButDifferentInstanceOfSameClassCanEnter() {
        val b = DevicePage("B")
        push(b)
        var completed = false
        var failed = false
        compose.runOnIdle {
            navigator.push {
                destination = b
                routeFailureCallback = { failed = true }
                routeCompletedCallback = { completed = true }
            }
        }
        await { completed }
        compose.runOnIdle { assertTrue(failed) }
        assertStack(a, b)
        show("Page B")
        val second = DevicePage("B2")
        push(second)
        assertStack(a, b, second)
        show("Page B2")
    }

    @Test
    fun removedAndReinsertedInstanceSurvivesOldApproval() {
        val b = DevicePage("B")
        push(b)
        val gate = gate()
        var approved = false
        intercept {
            val response = proceed()
            approved = true
            gate.await()
            response
        }
        pressBack()
        await { approved }
        compose.runOnIdle { navigator.pop { greenChannel = true } }
        assertStack(a)
        push(b)
        compose.runOnIdle { gate.complete(Unit) }
        assertStack(a, b)
        show("Page B")
    }

    @Test
    fun demoUrlPushResultConsumerAndSystemBackWorkTogether() {
        compose.runOnIdle {
            navigator.push { destination = Router.Home(); isCleanStack = true }
        }
        show("首页")
        click("@Router 拦截器详情页")
        show("参数详情")
        click("打开结果回传页（Consumer）")
        click("关闭并携带结果返回")
        show("from result")
        compose.activityRule.scenario.recreate()
        show("参数详情")
        pressBack()
        show("首页")
        compose.runOnIdle { assertEquals(1, navigator.backStack.size) }
    }

    // Keep production feature journeys in the same harness: the demo Navigator is process-wide
    // and snapshots global interceptors on first use, after the outer rule registers them.
    @Test
    fun productUrlAndTypedHelpResultSurviveActivityRecreation() {
        compose.runOnIdle {
            navigator.push { destination = Router.Home(); isCleanStack = true }
        }
        show("首页")
        click("打开商品详情")
        show("商品编号：42")
        click("咨询商品")
        show("帮助主题：商品 42")
        click("问题已解决")
        show("咨询结果：已解决（1 次）")
        compose.activityRule.scenario.recreate()
        show("咨询结果：已解决（1 次）")
        pressBack()
        show("首页")
        compose.runOnIdle { assertEquals(1, navigator.backStack.size) }
    }

    @Test
    fun accountInstanceAndSharedHelpUrlReturnToTheirCaller() {
        compose.runOnIdle {
            navigator.push { destination = Router.Home(); isCleanStack = true }
        }
        show("首页")
        click("打开账户中心")
        show("用户名：小明")
        click("账户帮助")
        show("帮助主题：账户服务")
        click("问题已解决")
        show("处理结果：已解决（1 次）")
        click("账户帮助")
        pressBack()
        show("处理结果：已解决（1 次）")
        pressBack()
        show("首页")
        compose.runOnIdle { assertEquals(1, navigator.backStack.size) }
    }

    private fun gate() = CompletableDeferred<Unit>().also { gates += it }

    private fun intercept(handler: suspend IRouterPopInterceptor.Chain.() -> PopResponse) {
        compose.runOnIdle { DeviceTestModule.handler = handler }
    }

    private fun push(page: DevicePage) {
        compose.runOnIdle { navigator.push(page) }
        await { navigator.last() === page }
        show("Page ${page.name}")
    }

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) { compose.runOnIdle(condition) }
    }

    private fun assertStack(vararg pages: IRouterEntryProvider) {
        await {
            navigator.backStack.size == pages.size &&
                pages.indices.all { navigator.backStack[it] === pages[it] }
        }
    }

    private fun show(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun click(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
    }
}

/** Test APK only: registration is idempotent; the delegate is reset after every test. */
private object DeviceTestModule : ModuleRouter {
    var handler: (suspend IRouterPopInterceptor.Chain.() -> PopResponse)? = null
    val failures = mutableListOf<Throwable>()
    private val interceptor = object : IRouterPopInterceptor {
        override suspend fun intercept(chain: IRouterPopInterceptor.Chain): PopResponse = try {
            handler?.invoke(chain) ?: chain.proceed()
        } catch (error: Throwable) {
            if (error !is CancellationException) failures += error
            throw error
        }
    }

    override fun routeMasterKV(): Map<KClass<*>, ParseRouterInfo> = emptyMap()
    override fun routeAliasesKV(): Map<String, KClass<*>> = emptyMap()
    override fun globalPushInterceptors(): List<GlobalPushInterceptorInfo> = emptyList()
    override fun globalPopInterceptors() = listOf(GlobalPopInterceptorInfo(interceptor, 0))
    override fun entryProviderInstaller(): EntryProviderInstaller = {
        entry<DevicePage>(clazzContentKey = { it.contentKey() }) { it.UI() }
    }
}

private class DevicePage(val name: String) : IRouterEntryProvider {
    var next by mutableStateOf<DevicePage?>(null)
    val results = mutableListOf<RouteResult<String>>()

    @Composable
    override fun UI() {
        var deliveries by rememberSaveable { mutableIntStateOf(0) }
        var value by rememberSaveable { mutableStateOf("none") }
        val consumer = rememberRouteResult<String>(key = "device-result") { result ->
            results += result
            deliveries++
            value = result.get() ?: "none"
        }
        DemoScreen(
            eyebrow = "Device verification",
            title = "Page $name",
            subtitle = "Controlled asynchronous pop and result delivery",
        ) {
            Column {
                Text("$name deliveries: $deliveries")
                Text("$name result: $value")
                next?.let { page ->
                    Button(onClick = { navigator.pushForResult(page, resultTo = consumer) }) {
                        Text("Open ${page.name}")
                    }
                }
                Button(onClick = { navigator.pop(result = "from-$name") }) {
                    Text("Return $name")
                }
            }
        }
    }
}
