package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.limuyang2.router.LocalNavigator

/**
 * singleTask 验证页：页面声明 @Router(singleTask = true)，演示「已在栈中 → 复用拉顶」。
 * 页面显示实例标识与本地计数，复用后计数保留即可证明状态未丢。
 */
@Composable
fun SingleTaskPage(entry: Router.SingleTask) {
    val navigator = LocalNavigator.current

    DemoBackHandler(true) {
        navigator.pop()
    }

    var counter by rememberSaveable { mutableIntStateOf(0) }

    DemoScreen(
        eyebrow = "导航栈 · singleTask",
        title = "SingleTask · 复用页",
        subtitle = "本页声明 @Router(singleTask = true)。先叠加中间页，再从中间页用 URL 重新打开本页。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "实例观察点",
            body = "复用拉顶后实例标识不变、计数保留；本次 push 的新参数不会注入旧实例。",
        ) {
            DemoBulletRow(label = "实例标识", value = entry.toString())
            DemoBulletRow(label = "本地计数", value = counter.toString())
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoActionButton(
                title = "计数 +1",
                description = "修改本地状态；复用拉顶后应保持不变。",
                onClick = { counter += 1 },
            )
            DemoActionButton(
                title = "叠加中间页",
                description = "普通 push，中间页可以继续叠加。",
                onClick = { navigator.push(Router.SingleTaskMiddle()) },
            )
        }
    }
}

@Composable
fun SingleTaskMiddlePage() {
    val navigator = LocalNavigator.current

    DemoBackHandler(true) {
        navigator.pop()
    }

    DemoScreen(
        eyebrow = "导航栈 · singleTask",
        title = "SingleTask · 中间页",
        subtitle = "从这一页用 URL 重新打开 SingleTask 页，观察它复用栈中实例并清掉上方页面。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "当前场景",
            body = "URL 打开 test://test/single-task 时注解默认 singleTask 生效：已有实例回到栈顶，本页与其它夹层按 Cancelled 移除。",
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoActionButton(
                title = "再叠一层中间页",
                description = "继续普通 push，验证复用时多层夹层一起清理。",
                onClick = { navigator.push(Router.SingleTaskMiddle()) },
            )
            DemoActionButton(
                title = "URL 重新打开 SingleTask 页",
                description = "预期：复用栈中已有实例回到栈顶，实例标识不变、计数保留。",
                onClick = { navigator.push("test://test/single-task") },
            )
        }
    }
}
