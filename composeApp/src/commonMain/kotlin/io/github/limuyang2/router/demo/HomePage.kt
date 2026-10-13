package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.limuyang2.router.IRouterEntryProvider
import io.github.limuyang2.router.demo.feature.account.AccountPage
import io.github.limuyang2.router.rememberRouteResult
import kotlinx.collections.immutable.persistentListOf

@Composable
fun HomePage() {
    var backToResult by remember { mutableStateOf<String?>(null) }
    rememberRouteResult<DetailResult>(key = "home-result") { result ->
        backToResult = result.get()?.str
    }

    DemoScreen(
        eyebrow = "Router Demo",
        title = "首页",
        subtitle = "路由功能演示入口，包含 URL 跳转、实例跳转、清栈跳转和未知页面兜底。",
    ) {
        if (backToResult != null) {
            DemoInfoCard(
                title = "定向回退结果",
                body = "通过 pop { to(Home, result, key) } 收到：$backToResult",
                tone = Color(0xFFF0FFF4),
            )
            DemoSectionSpacer()
        }

        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            DemoActionButton(
                title = "打开商品详情",
                description = "查看商品并咨询帮助，返回后查看处理结果。",
                onClick = { navigator.push("test://modules/product?id=42") },
            )

            DemoActionButton(
                title = "打开账户中心",
                description = "查看账户并进入帮助中心。",
                accent = Color(0xFF256D85),
                onClick = { navigator.push(AccountPage(name = "小明")) },
            )

            DemoActionButton(
                title = "打开 Web 页面",
                description = "全局 URL 拦截器将 https 链接改写为 Router.Web。",
                accent = Color(0xFF284B63),
                onClick = {
                    navigator.push("https://baidu.com?id=1")
                },
            )

            DemoActionButton(
                title = "@Router 拦截器详情页",
                description = "命中 test://test/detail，使用 @Router 声明的页面拦截器注入 names。",
                accent = Color(0xFF5B4B8A),
                onClick = {
                    navigator.push("test://test/detail?ids=1&source=2")
                },
            )

            DemoActionButton(
                title = "Owner 拦截器详情页",
                description = "命中 test://test/owner-detail，使用 @RouterOwnerPushInterceptor 外置绑定注入 names。",
                accent = Color(0xFF256D85),
                onClick = {
                    navigator.push("test://test/owner-detail?ids=7&source=8")
                },
            )

            DemoActionButton(
                title = "实例化跳转并清空栈",
                description = "直接构造 Router.Detail，并重置导航栈。",
                accent = Color(0xFF9A3412),
                onClick = {
                    navigator.push {
                        destination = Router.Detail(persistentListOf(3, 24), names = TestData("1"))
                        isCleanStack = true
                    }
                },
            )

            DemoActionButton(
                title = "打开未知页面",
                description = "触发 UnknownRoute 兜底，验证 404 处理。",
                accent = Color(0xFF7A1F2A),
                onClick = {
                    navigator.push(NNNN())
                },
            )

            DemoActionButton(
                title = "复现 pop 结果链问题",
                description = "进入 A/B/C 三层演示页，观察 B 自移除后 C 的结果如何影响 A。",
                accent = Color(0xFF6B21A8),
                onClick = {
                    navigator.push(Router.PopBugA())
                },
            )
        }
    }
}

class NNNN() : IRouterEntryProvider
