package io.github.limuyang2.router.demo.feature.product

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
import io.github.limuyang2.router.demo.feature.shared.HelpPage
import io.github.limuyang2.router.demo.feature.shared.HelpResult
import io.github.limuyang2.router.rememberRouteResult

/** URL parameters are generated in this module; the host only knows the route URL. */
@Router(scheme = "test", host = "modules", path = "product")
class ProductPage(@param:RouterParam("id") val id: Int) : IRouterEntryProvider {
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
        FeatureScreen(title = "商品详情", onBack = { navigator.pop() }) {
            FeatureCard("实际收到的参数", "预期：商品编号为 URL 中的 id。") {
                Text("商品编号：$id")
            }
            FeatureCard("实际接收结果", "预期：帮助页返回后显示“已解决”，每次成功返回接收次数加一。") {
                Text(answer?.let { "咨询结果：$it（$deliveries 次）" } ?: "尚未收到结果")
            }
            FeatureAction("咨询商品", "通过实例打开共享模块的帮助页，再返回验证结果。", {
                navigator.pushForResult(HelpPage("商品 $id"), resultTo = helpResult)
            })
        }
    }
}
