package io.github.limuyang2.router.demo.feature.shared

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.limuyang2.router.IRouterEntryProvider
import io.github.limuyang2.router.LocalNavigator
import io.github.limuyang2.router.anno.Router
import io.github.limuyang2.router.anno.RouterParam

/** Result shared by callers in both feature modules. */
data class HelpResult(val topic: String, val answer: String)

/** Reached through either feature's api dependency, without a direct host dependency. */
@Router(scheme = "test", host = "modules", path = "help")
class HelpPage(@param:RouterParam("topic") val topic: String = "general") : IRouterEntryProvider {
    @Composable
    override fun UI() {
        val navigator = LocalNavigator.current
        FeatureScreen(title = "帮助中心", onBack = { navigator.pop() }) {
            val description = when (topic) {
                "account" -> "账户服务"
                "general" -> "使用帮助"
                else -> topic
            }
            Text("帮助主题：$description")
            Text("确认问题已解决后，处理结果会带回刚才的页面。")
            Button(onClick = { navigator.pop(HelpResult(topic, "已解决")) }) {
                Text("问题已解决")
            }
        }
    }
}
