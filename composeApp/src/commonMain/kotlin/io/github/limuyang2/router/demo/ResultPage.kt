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
        subtitle = "验证 pop 返回策略：结果先进入调用方 entry，再由 consumer 或 state token 消费。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "返回策略",
            body = "当前页面支持 pop(result) 和带 key 的 pop { to(...) }。上一页推荐通过 rememberRouteResult 接收结果。",
            tone = androidx.compose.ui.graphics.Color(0xFFF9F7FF),
        )

        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            DemoActionButton(
                title = "回退到首页并带结果",
                description = "执行 pop { to<DetailResult>(Router.Home::class, DetailResult(\"from result\"), key = \"home-result\") }。",
                accent = androidx.compose.ui.graphics.Color(0xFF5B4B8A),
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

            DemoActionButton(
                title = "关闭并携带结果返回",
                description = "执行 pop(result = DetailResult(\"from result\"))，结果交给上一页。",
                accent = androidx.compose.ui.graphics.Color(0xFF8A3B12),
                onClick = {
                    navigator.pop(result = DetailResult("from result"))
                },
            )
        }

        Text(
            text = "如果上一页绑定了 consumer token 或 state token，这里返回的数据会在它重新进入前台后被消费。",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.ui.graphics.Color(0xFF475467),
        )
    }
}
