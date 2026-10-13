package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun ResultPage() {
    DemoBackHandler(true) {
        navigator.pop()
    }

    DemoScreen(
        eyebrow = "结果回传",
        title = "结果回传",
        subtitle = "选择一种返回方式，再到发起页面对照实际接收结果。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "返回策略",
            body = "携带结果应收到 Success；不带结果应收到 NoResult；定向回首页会移除中间页面，并将结果交给首页的 home-result 接收器。",
        )

        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            if (navigator.backStack.any { it is Router.Home }) {
                DemoActionButton(
                    title = "回退到首页并带结果",
                    description = "预期：首页收到 from result，中间页面移除。",
                    onClick = {
                        navigator.pop {
                            to<DetailResult>(
                                clazz = Router.Home::class,
                                result = DetailResult("from result"),
                                key = "home-result",
                            )
                        }
                    },
                )
            }

            DemoActionButton(
                title = "关闭并携带结果返回",
                description = "执行 pop(result = DetailResult(\"from result\"))，结果交给上一页。",
                onClick = {
                    navigator.pop(result = DetailResult("from result"))
                },
            )
            DemoActionButton(
                title = "不带结果返回",
                description = "预期：发起页收到 NoResult。",
                onClick = { navigator.pop() },
            )
        }

        Text(
            text = "如果上一页绑定了 consumer token 或 state token，这里返回的数据会在它重新进入前台后被消费。",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
