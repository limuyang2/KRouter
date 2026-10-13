package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.limuyang2.router.rememberRouteResult
import kotlinx.collections.immutable.ImmutableCollection

@Composable
fun DetailPage(id: ImmutableCollection<Int>, names: TestData, source: Int) {
    DemoBackHandler(true) {
        navigator.pop()
    }

    var lastResult by remember { mutableStateOf<String?>(null) }
    val detailResultConsumer = rememberRouteResult<DetailResult> { result ->
        lastResult = result.get()?.str
    }
    val urlResultConsumer = rememberRouteResult<Any>(key = "url-result") { result ->
        lastResult = result.get().toString()
    }

    DemoScreen(
        eyebrow = "参数注入",
        title = "参数详情",
        subtitle = "展示参数注入和页面拦截器效果。@Router 内联绑定和 owner 外置绑定都会额外写入 names 参数。",
        onBack = { navigator.pop() },
    ) {
        DemoInfoCard(
            title = "收到的参数",
            body = "支持 URL 进入和实例直接 push 两种方式，下方为最终注入结果。",
        ) {
            DemoSectionSpacer()
            DemoBulletRow(label = "ids", value = id.joinToString(prefix = "[", postfix = "]"))
            DemoSectionSpacer()
            DemoBulletRow(label = "names", value = names.toString())
            DemoSectionSpacer()
            DemoBulletRow(label = "source", value = source.toString())
        }

        if (lastResult != null) {
            DemoInfoCard(
                title = "Consumer 回传结果",
                body = "通过 rememberRouteResult 绑定的结果：",
                tone = Color(0xFFF0FFF4),
            ) {
                DemoSectionSpacer()
                DemoBulletRow(label = "result", value = lastResult!!)
            }
        }

        DemoInfoCard(
            title = "继续导航",
            body = "进入结果回传页，返回时观察结果消费。下方分别演示实例 push 和 URL push。",
            tone = Color(0xFFF7FAFC),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DemoActionButton(
                    title = "打开结果回传页（Consumer）",
                    description = "使用 rememberRouteResult 绑定 DetailResult。",
                    accent = Color(0xFF355C7D),
                    onClick = {
                        navigator.pushForResult(Router.Result(null), resultTo = detailResultConsumer)
                    },
                )

                DemoActionButton(
                    title = "打开结果回传页（RouteResult callback）",
                    description = "直接使用 pushForResult，回调里打印完整返回结果。",
                    accent = Color(0xFF2F6F5E),
                    onClick = {
                        navigator.pushForResult<DetailResult>(Router.Result(null)) { result ->
                            println("DetailPage callback result = $result")
                        }
                    },
                )

                DemoActionButton(
                    title = "打开结果回传页（URL 方式）",
                    description = "URL 场景下使用 rememberRouteResult 绑定结果宿主。",
                    accent = Color(0xFF6C4F3D),
                    onClick = {
                        navigator.pushForResult("xxxx://test/result", resultTo = urlResultConsumer)
                    },
                )
            }
        }
    }
}

data class DetailResult(val str: String)
