package io.github.limuyang2.router.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.limuyang2.router.LocalNavigator
import io.github.limuyang2.router.UnknownRoute

@Composable
fun FallbackPage(route: Any) {
    val navigator = LocalNavigator.current
    val unknownRoute = route as? UnknownRoute
    val routeInfo = if (unknownRoute != null) {
        unknownRoute.uri?.toString() ?: "null"
    } else {
        route::class.simpleName ?: "Unknown"
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "😕",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "404",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "页面未找到",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = routeInfo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { navigator.pop() }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val arrowColor = MaterialTheme.colorScheme.onPrimary
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val w = size.width
                        val h = size.height
                        val strokeWidth = 2.5f * density
                        drawLine(arrowColor, Offset(w * 0.78f, h / 2f), Offset(w * 0.22f, h / 2f), strokeWidth, StrokeCap.Round)
                        drawLine(arrowColor, Offset(w * 0.22f, h / 2f), Offset(w * 0.48f, h * 0.2f), strokeWidth, StrokeCap.Round)
                        drawLine(arrowColor, Offset(w * 0.22f, h / 2f), Offset(w * 0.48f, h * 0.8f), strokeWidth, StrokeCap.Round)
                    }
                    Text("返回上一页")
                }
            }
        }
    }
}
