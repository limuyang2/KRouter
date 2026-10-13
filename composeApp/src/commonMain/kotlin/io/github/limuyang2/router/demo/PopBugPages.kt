package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.limuyang2.router.LocalNavigator
import io.github.limuyang2.router.RouteResult
import io.github.limuyang2.router.rememberRouteResult

@Composable
fun PopBugAHomePage() {
    DemoBackHandler(true) {
        navigator.pop()
    }

    var valueResultLabel by remember { mutableStateOf("尚未收到值结果") }
    var fullResultLabel by remember { mutableStateOf("尚未收到完整结果") }
    var launchCount by remember { mutableIntStateOf(0) }
    val fullResultConsumer = rememberRouteResult<String>(key = "a-full-result") { result ->
        fullResultLabel = result.toDebugLabel()
    }
    val valueResultConsumer = rememberRouteResult<String>(key = "a-value-result") { result ->
        valueResultLabel = result.get() ?: "null"
    }

    DemoScreen(
        eyebrow = "Pop Repro",
        title = "A 页面",
        subtitle = "A 等待 B 的结果。B 会打开 C 并把自己移除，用来复现结果链断开的场景。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "当前观察点",
            body = "A 同时展示完整 RouteResult 和 result.get() 的业务值。问题复现时，完整结果通常会变成 Cancelled，值结果会是 null。",
            tone = Color(0xFFF9F7FF),
        ) {
            DemoSectionSpacer()
            DemoBulletRow(label = "启动次数", value = launchCount.toString())
            DemoSectionSpacer()
            DemoBulletRow(label = "完整结果", value = fullResultLabel)
            DemoSectionSpacer()
            DemoBulletRow(label = "值结果", value = valueResultLabel)
        }

        DemoInfoCard(
            title = "复现步骤",
            body = "1. 从这里进入 B。\n2. 在 B 里让它打开 C 并移除自己。\n3. 在 C 返回字符串结果。\n4. 回到 A 观察结果显示。",
            tone = Color(0xFFF7FAFC),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoActionButton(
                title = "进入 B（A 用完整结果接收）",
                description = "A 通过 rememberRouteResult 绑定 Result<B>，方便看到 Cancelled / NoResult / Success。",
                accent = Color(0xFF355C7D),
                onClick = {
                    launchCount += 1
                    navigator.pushForResult(Router.PopBugB(), resultTo = fullResultConsumer)
                },
            )

            DemoActionButton(
                title = "进入 B（A 用值结果接收）",
                description = "A 通过 rememberRouteResult 绑定结果，再用 get() 读取业务值。",
                accent = Color(0xFF2F6F5E),
                onClick = {
                    launchCount += 1
                    navigator.pushForResult(Router.PopBugB(), resultTo = valueResultConsumer)
                },
            )

            DemoActionButton(
                title = "进入 B（RouteResult callback）",
                description = "使用 pushForResult<String>。完整结果会打印到控制台，同时页面上的两块状态不会更新。",
                accent = Color(0xFF7A4B24),
                onClick = {
                    launchCount += 1
                    navigator.pushForResult<String>(Router.PopBugB()) { result ->
                        println("PopBugA callback result = $result")
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

    var localResultLabel by remember { mutableStateOf("B 还没收到 C 的结果") }
    val bResultConsumer = rememberRouteResult<String>(key = "b-full-result") { result ->
        localResultLabel = result.toDebugLabel()
    }

    DemoScreen(
        eyebrow = "Pop Repro",
        title = "B 页面",
        subtitle = "B 是中间页。这里可以正常等 C，也可以在打开 C 后把 B 自己移除。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "B 自己的观察点",
            body = "如果 B 保持存活，它能收到 C 的完整结果。若 B 在打开 C 后被移除，这块状态通常不会再更新，因为 B 已经不在栈里。",
            tone = Color(0xFFF0FFF4),
        ) {
            DemoSectionSpacer()
            DemoBulletRow(label = "B 等 C 的结果", value = localResultLabel)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoActionButton(
                title = "正常打开 C",
                description = "B 继续存活，C 返回后结果先回给 B。这条路径用来做对照。",
                accent = Color(0xFF4C6A92),
                onClick = {
                    navigator.pushForResult(Router.PopBugC(), resultTo = bResultConsumer)
                },
            )

            DemoActionButton(
                title = "打开 C 并移除 B",
                description = "关键复现路径：push C 后立刻把 B 从栈里移掉，B 不再声明等待 C 的结果。随后在 C 返回结果，再回到 A 观察。",
                accent = Color(0xFF9A3412),
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
        eyebrow = "Pop Repro",
        title = "C 页面",
        subtitle = "C 只负责返回结果。重点是看它返回后，结果究竟交给了谁。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "当前场景",
            body = "如果是从 B 正常打开进来，结果会回给 B。如果 B 在打开你之前已经决定自我移除，那么这里返回时 caller 实际已经不存在。",
            tone = Color(0xFFFFF7ED),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoActionButton(
                title = "返回字符串结果",
                description = "执行 pop(result = \"result-from-c\")。",
                accent = Color(0xFF7C3AED),
                onClick = {
                    navigator.pop(result = "result-from-c")
                },
            )

            DemoActionButton(
                title = "不带结果直接关闭",
                description = "执行 pop()，用于对照 NoResult 路径。",
                accent = Color(0xFF475467),
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
