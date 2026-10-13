package io.github.limuyang2.router.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.limuyang2.router.rememberRouteResult

/** Separate result and stack scenarios from parameter injection examples. */
@Composable
fun VerificationPage(mode: String) {
    var actual by rememberSaveable { mutableStateOf<String?>(null) }
    val consumer = rememberRouteResult<DetailResult> { actual = it.toString() }
    val urlConsumer = rememberRouteResult<DetailResult>(key = "verify-url") { actual = it.toString() }
    val stackMode = mode == "stack"
    DemoScreen(
        eyebrow = if (stackMode) "导航栈" else "结果回传",
        title = if (stackMode) "清栈与根页保护" else "结果接收验证",
        subtitle = if (stackMode) "观察顶部导航栈：清栈后只剩一个页面，根页执行 pop 后仍保留。" else "选择接收方式，在下一页返回结果或不带结果关闭，然后查看实际反馈。",
        onBack = if (navigator.backStack.size > 1) ({ navigator.pop() }) else null,
    ) {
        if (stackMode) {
            DemoInfoCard("验证步骤", "1. 执行清栈。\n2. 确认导航栈只剩 Verification。\n3. 尝试根页返回，页面应保留。\n4. 重建首页继续其他用例。")
            DemoActionButton("清栈进入新的验证页", "预期：旧页面全部移除，只剩当前新页面。", {
                navigator.push { destination = Router.Verification("stack"); isCleanStack = true }
            })
            DemoActionButton("尝试返回", "预期：多页时返回上一页；只有根页时保持当前页。", { navigator.pop() })
            DemoActionButton("重建首页", "清栈并打开首页，恢复其他功能的验证入口。", {
                navigator.push { destination = Router.Home(); isCleanStack = true }
            })
        } else {
            DemoInfoCard("实际接收结果", actual ?: "尚未执行，请选择下方操作。")
            DemoInfoCard("预期结果", "携带结果：Success(value=DetailResult(str=from result))。\n不带结果：NoResult。\n定向回首页：首页显示相同业务值，中间页面移除。")
            DemoActionButton("实例跳转并接收", "构造 Router.Result，通过 Consumer 接收完整结果。", {
                actual = "等待 Consumer 结果…"
                navigator.pushForResult(Router.Result(null), resultTo = consumer)
            })
            DemoActionButton("URL 跳转并接收", "xxxx://test/result，与实例跳转对照。", {
                actual = "等待 URL 结果…"
                navigator.pushForResult("xxxx://test/result", resultTo = urlConsumer)
            })
        }
    }
}
