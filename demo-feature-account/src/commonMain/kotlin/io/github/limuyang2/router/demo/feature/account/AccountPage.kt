package io.github.limuyang2.router.demo.feature.account

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.limuyang2.router.IRouterEntryProvider
import io.github.limuyang2.router.LocalNavigator
import io.github.limuyang2.router.anno.Router
import io.github.limuyang2.router.anno.RouterParam
import io.github.limuyang2.router.demo.feature.shared.FeatureScreen
import io.github.limuyang2.router.demo.feature.shared.FeatureCard
import io.github.limuyang2.router.demo.feature.shared.FeatureAction
import io.github.limuyang2.router.demo.feature.shared.HelpResult
import io.github.limuyang2.router.rememberRouteResult

/** Demonstrates a typed host destination and a URL destination in a transitive router module. */
@Router(scheme = "test", host = "modules", path = "account")
class AccountPage(@param:RouterParam("name") val name: String = "访客") : IRouterEntryProvider {
    @Composable
    override fun UI() {
        val navigator = LocalNavigator.current
        var answer by rememberSaveable { mutableStateOf<String?>(null) }
        var deliveries by rememberSaveable { mutableIntStateOf(0) }
        val helpResult = rememberRouteResult<HelpResult> { result ->
            result.get()?.let {
                answer = it.answer
                deliveries++
            }
        }
        FeatureScreen(title = "账户中心", onBack = { navigator.pop() }) {
            FeatureCard("实际收到的参数", "预期：用户名与实例构造参数一致。") {
                Text("用户名：$name")
            }
            FeatureCard("实际接收结果", "预期：帮助页返回后显示“已解决”，每次成功返回接收次数加一。") {
                Text(answer?.let { "处理结果：$it（$deliveries 次）" } ?: "尚未收到结果")
            }
            FeatureAction("账户帮助", "通过 URL 打开共享帮助页，预期主题为账户服务。", {
                navigator.pushForResult("test://modules/help?topic=account", resultTo = helpResult)
            })
        }
    }
}
