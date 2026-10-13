package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.limuyang2.router.LocalNavigator
import io.github.limuyang2.router.RouteResult
import io.github.limuyang2.router.rememberRouteResult

@Composable
fun PopBugAHomePage(resultState: DemoResultState) {
    DemoBackHandler(true) {
        navigator.pop()
    }

    var valueResultLabel by resultState.value
    var fullResultLabel by resultState.result
    var launchCount by rememberSaveable { mutableIntStateOf(0) }
    val fullResultConsumer = rememberRouteResult<String>(key = "a-full-result") { result ->
        fullResultLabel = result.toDebugLabel()
        valueResultLabel = result.get() ?: "null"
    }
    val valueResultConsumer = rememberRouteResult<String>(key = "a-value-result") { result ->
        valueResultLabel = result.get() ?: "null"
        fullResultLabel = result.toDebugLabel()
    }

    DemoScreen(
        eyebrow = "导航栈 · 结果取消",
        title = "A · 结果接收页",
        subtitle = "A 等待 B 的结果。对照正常打开 C 和替换 B 两条路径，观察导航栈与结果。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "当前观察点",
            body = "预期：B 被替换后，A 等待 B 的请求结束为 Cancelled，get() 为 null；C 返回值不会作为 B 的结果交给 A。",
        ) {
            DemoSectionSpacer()
            DemoBulletRow(label = "启动次数", value = launchCount.toString())
            DemoSectionSpacer()
            DemoBulletRow(label = "完整结果", value = fullResultLabel ?: "尚未收到完整结果")
            DemoSectionSpacer()
            DemoBulletRow(label = "值结果", value = valueResultLabel ?: "尚未收到值结果")
        }

        DemoInfoCard(
            title = "验证步骤",
            body = "1. 从这里进入 B。\n2. 在 B 里让它打开 C 并移除自己。\n3. 在 C 返回字符串结果。\n4. 回到 A 观察结果显示。",
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoActionButton(
                title = "进入 B（A 用完整结果接收）",
                description = "A 通过 rememberRouteResult<String> 接收完整结果，观察 Cancelled / NoResult / Success。",
                onClick = {
                    launchCount += 1
                    navigator.pushForResult(Router.PopBugB(), resultTo = fullResultConsumer)
                },
            )

            DemoActionButton(
                title = "进入 B（A 用值结果接收）",
                description = "A 通过 rememberRouteResult 绑定结果，再用 get() 读取业务值。",
                onClick = {
                    launchCount += 1
                    navigator.pushForResult(Router.PopBugB(), resultTo = valueResultConsumer)
                },
            )

            DemoActionButton(
                title = "进入 B（RouteResult callback）",
                description = "使用 pushForResult<String>，回调结果直接显示在上方。",
                onClick = {
                    launchCount += 1
                    navigator.pushForResult<String>(Router.PopBugB()) { result ->
                        fullResultLabel = result.toDebugLabel()
                        valueResultLabel = result.get() ?: "null"
                    }
                },
            )
        }
    }
}

@Composable
fun PopBugBPage() {
    DemoBackHandler(true) {
        navigator.pop()
    }

    var localResultLabel by rememberSaveable { mutableStateOf("B 还没收到 C 的结果") }
    val bResultConsumer = rememberRouteResult<String>(key = "b-full-result") { result ->
        localResultLabel = result.toDebugLabel()
    }

    DemoScreen(
        eyebrow = "导航栈 · 中间页",
        title = "B · 中间页",
        subtitle = "B 是中间页。这里可以正常等 C，也可以在打开 C 后把 B 自己移除。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "B 自己的观察点",
            body = "如果 B 保持存活，它能收到 C 的完整结果。若 B 在打开 C 后被移除，这块状态通常不会再更新，因为 B 已经不在栈里。",
        ) {
            DemoSectionSpacer()
            DemoBulletRow(label = "B 等 C 的结果", value = localResultLabel)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoActionButton(
                title = "正常打开 C",
                description = "B 继续存活，C 返回后结果先回给 B。这条路径用来做对照。",
                onClick = {
                    navigator.pushForResult(Router.PopBugC(), resultTo = bResultConsumer)
                },
            )

            DemoActionButton(
                title = "B 携带结果返回 A",
                description = "预期：A 收到 Success(value=result-from-b)，与替换 B 的 Cancelled 对照。",
                onClick = { navigator.pop(result = "result-from-b") },
            )

            DemoActionButton(
                title = "打开 C 并移除 B",
                description = "预期：栈中移除 B，A 等待 B 的请求被取消。在 C 返回后回到 A 观察。",
                onClick = {
                    navigator.push {
                        destination = Router.PopBugC()
                        isPopCurrent = true
                    }
                },
            )
        }
    }
}

@Composable
fun PopBugCPage() {
    val navigator = LocalNavigator.current

    DemoBackHandler(true) {
        navigator.pop()
    }

    DemoScreen(
        eyebrow = "导航栈 · 返回结果",
        title = "C · 结果发送页",
        subtitle = "C 只负责返回结果。重点是看它返回后，结果究竟交给了谁。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "当前场景",
            body = "正常路径：结果交给 B 的接收器。替换 B 的路径：C 没有绑定结果接收器；A 等待 B 的请求已取消，C 的结果不会交给 A。",
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoActionButton(
                title = "返回字符串结果",
                description = "执行 pop(result = \"result-from-c\")。",
                onClick = {
                    navigator.pop(result = "result-from-c")
                },
            )

            DemoActionButton(
                title = "不带结果直接关闭",
                description = "执行 pop()，用于对照 NoResult 路径。",
                onClick = {
                    navigator.pop()
                },
            )
        }
    }
}

private fun RouteResult<String>.toDebugLabel(): String {
    return when (this) {
        is RouteResult.Success -> "Success(value=$value)"
        RouteResult.NoResult -> "NoResult"
        RouteResult.Cancelled -> "Cancelled"
        is RouteResult.TypeMismatch -> "TypeMismatch(expected=$expected, actual=$actual)"
    }
}
