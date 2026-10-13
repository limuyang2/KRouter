package io.github.limuyang2.router.demo.feature.product

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
            Text("商品编号：$id")
            Text("需要了解商品时，可以前往帮助中心咨询。")
            answer?.let { Text("咨询结果：$it（$deliveries 次）") }
            Button(onClick = { navigator.pushForResult(HelpPage("商品 $id"), resultTo = helpResult) }) {
                Text("咨询商品")
            }
        }
    }
}
