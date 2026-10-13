package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun WebPage(url: String) {
    DemoScreen(
        eyebrow = "拦截器 · 全局重定向",
        title = "Web 重定向验证",
        subtitle = "预期：http/https 被改写为 Router.Web，原始链接完整保留。此页用于观察重定向结果。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "实际收到的 URL",
            body = url,
        )

        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            DemoActionButton(
                title = "返回上一页",
                description = "关闭当前 Web 占位页，回到触发跳转的位置。",
                onClick = { navigator.pop() },
            )
        }

        Text(
            text = "验证完成后返回入口，继续验证页面级拦截器。",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
