package io.github.limuyang2.router.demo.feature.account

import androidx.compose.material3.Button
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
            Text("用户名：$name")
            Text("遇到账户问题时，可以前往帮助中心处理。")
            answer?.let { Text("处理结果：$it（$deliveries 次）") }
            Button(onClick = { navigator.pushForResult("test://modules/help?topic=account", resultTo = helpResult) }) {
                Text("账户帮助")
            }
        }
    }
}
