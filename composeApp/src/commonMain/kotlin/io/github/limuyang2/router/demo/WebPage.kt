package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun WebPage(url: String) {
    println("打开网页：$url")

    DemoScreen(
        eyebrow = "Web Interceptor",
        title = "Fake Web Page",
        subtitle = "这里不真正渲染浏览器，而是确认 http/https 已经被拦截并改写成 Router.Web。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "当前 URL",
            body = url,
            tone = androidx.compose.ui.graphics.Color(0xFFF6FAFF),
        )

        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            DemoActionButton(
                title = "返回上一页",
                description = "关闭当前 Web 占位页，回到触发跳转的位置。",
                accent = androidx.compose.ui.graphics.Color(0xFF284B63),
                onClick = { navigator.pop() },
            )
        }

        Text(
            text = "如果后续要接真实 WebView，可以把 Router.Web 的 UI 替换为平台实际承载。",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.ui.graphics.Color(0xFF475467),
        )
    }
}
