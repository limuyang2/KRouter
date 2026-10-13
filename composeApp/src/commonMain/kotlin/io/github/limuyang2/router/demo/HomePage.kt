package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.limuyang2.router.IRouterEntryProvider
import io.github.limuyang2.router.demo.feature.account.AccountPage
import io.github.limuyang2.router.rememberRouteResult
import kotlinx.collections.immutable.persistentListOf

@Composable
fun HomePage() {
    var category by rememberSaveable { mutableStateOf("跳转与参数") }
    var backToResult by rememberSaveable { mutableStateOf<String?>(null) }
    rememberRouteResult<DetailResult>(key = "home-result") { backToResult = it.toString() }
    DemoScreen(
        eyebrow = "KRouter · 功能验证",
        title = "路由验证台",
        subtitle = "选择功能 → 执行用例 → 对照预期。每页展示当前导航栈，返回结果直接显示在页面上。",
    ) {
        backToResult?.let {
            DemoInfoCard("定向返回 · 实际结果", it)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("跳转与参数", "拦截器", "结果回传", "导航栈", "跨模块").forEach { name ->
                FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) })
            }
        }
        when (category) {
            "跳转与参数" -> {
                DemoCase("01 · URL 参数解析", "test://test/owner-detail?ids=7&source=8", "目标页显示 ids=[7]、source=8；names 由 Owner 拦截器注入。") {
                    navigator.push("test://test/owner-detail?ids=7&source=8")
                }
                DemoCase("02 · 实例跳转", "Router.Detail(ids=[3, 24], source=0)", "目标页显示 ids=[3, 24]、source=0，导航栈增加一页。") {
                    navigator.push(Router.Detail(persistentListOf(3, 24), names = TestData("实例参数")))
                }
                DemoCase("03 · 未注册 URL", "test://missing/page", "URL 匹配失败后，Demo 在失败回调中打开兜底页，显示原始 URL；返回后仍在验证台。") {
                    navigator.openUnknownUrlDemo("test://missing/page")
                }
                DemoCase("04 · 未注册实例", "NNNN()", "进入统一兜底页，显示未注册实例的类型。") { navigator.push(NNNN()) }
            }
            "拦截器" -> {
                DemoCase("05 · 全局 URL 重定向", "https://baidu.com?id=1", "进入 Web 重定向验证页，原始 URL 保持完整。") { navigator.push("https://baidu.com?id=1") }
                DemoCase("06 · @Router 页面拦截器", "test://test/detail?ids=1&source=2", "显示 ids=[1]、source=2 和最终 names；此路由同时绑定内联和 Owner 拦截器。") { navigator.push("test://test/detail?ids=1&source=2") }
                DemoCase("07 · Owner 外置拦截器", "test://test/owner-detail?ids=7&source=8", "names 显示 TestData(a=Owner interceptor data)。") { navigator.push("test://test/owner-detail?ids=7&source=8") }
            }
            "结果回传" -> {
                DemoCase("08 · 结果接收方式", "Consumer / callback / URL / 定向返回", "在独立验证页发起请求，返回后观察 Success 或 NoResult；定向结果显示在首页。") { navigator.push(Router.Verification("results")) }
                DemoCase("09 · 中间页移除与结果取消", "A → B → C", "正常路径：B 收到 C 的结果；替换 B：A 收到 Cancelled，C 的结果不会冒充 B 的结果。") { navigator.push(Router.PopBugA()) }
            }
            "导航栈" -> {
                DemoCase("10 · 清栈与根页保护", "isCleanStack / pop", "清栈后只剩一个验证页；根页返回不会清空导航栈，可重建首页继续验证。") { navigator.push(Router.Verification("stack")) }
                DemoCase("11 · 替换当前页", "isPopCurrent = true", "在 B 打开 C 并移除 B，栈中不再包含 B，返回 C 后回到 A。") { navigator.push(Router.PopBugA()) }
            }
            "跨模块" -> {
                DemoCase("12 · 跨模块 URL 注册", "test://modules/product?id=42", "商品页显示 id=42；打开共享帮助页并返回后，显示“已解决”和接收次数。") { navigator.push("test://modules/product?id=42") }
                DemoCase("13 · 跨模块实例注册", "AccountPage(name=小明)", "账户页显示“小明”；通过 URL 打开共享帮助页并接收结果。") { navigator.push(AccountPage(name = "小明")) }
            }
        }
    }
}

@Composable
private fun DemoCase(title: String, input: String, expected: String, onClick: () -> Unit) {
    DemoInfoCard(title, "输入：$input") {
        DemoBulletRow("预期结果", expected)
        DemoActionButton("验证${title.substringAfter(" · ")}", "执行此用例，按目标页提示观察结果。", onClick)
    }
}

class NNNN : IRouterEntryProvider
