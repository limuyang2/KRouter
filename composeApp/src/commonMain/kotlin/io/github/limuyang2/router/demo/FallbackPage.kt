package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.limuyang2.router.UnknownRoute

@Composable
fun FallbackPage(route: Any) {
    val routeInfo = when (route) {
        is Router.UrlFallback -> route.url
        is UnknownRoute -> route.uri?.toString() ?: route.objClazz?.simpleName ?: "未知路由"
        else -> route::class.simpleName ?: "未知类型"
    }
    val isUrlFailure = route is Router.UrlFallback || (route is UnknownRoute && route.uri != null)
    DemoScreen(
        eyebrow = "FALLBACK · 路由异常处理",
        title = "路由兜底页",
        subtitle = "目标页面无法打开，已显示兜底内容。",
        onBack = { navigator.pop() },
        headerContent = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("404", style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("页面未找到", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        if (isUrlFailure) "这个链接没有匹配到已注册的页面。" else "这个页面实例没有对应的界面内容。",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    DemoActionButton("返回验证入口", "关闭兜底页，继续验证其他功能。", { navigator.pop() })
                }
            }
        },
    ) {
        DemoInfoCard("本次兜底验证", "这是预期的异常场景：目标无法显示时，提供明确提示和返回入口。") {
            DemoBulletRow(if (isUrlFailure) "原始 URL" else "页面实例类型", routeInfo)
            DemoBulletRow("触发方式", if (route is Router.UrlFallback) {
                "URL 跳转失败 → routeFailureCallback → Demo 兜底页"
            } else {
                "未找到页面内容 → NavDisplay fallback"
            })
            DemoBulletRow("返回后", "恢复验证入口，关闭当前兜底页面。")
        }
    }
}
