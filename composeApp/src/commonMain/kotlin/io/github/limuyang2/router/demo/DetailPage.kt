package io.github.limuyang2.router.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.limuyang2.router.rememberRouteResult
import kotlinx.collections.immutable.ImmutableCollection

@Composable
fun DetailPage(id: ImmutableCollection<Int>, names: TestData, source: Int) {
    DemoBackHandler(true) {
        navigator.pop()
    }

    var lastResult by rememberSaveable { mutableStateOf<String?>(null) }
    var businessValue by rememberSaveable { mutableStateOf<String?>(null) }
    val detailResultConsumer = rememberRouteResult<DetailResult> { result ->
        lastResult = result.toString()
        businessValue = result.get()?.str
    }
    val urlResultConsumer = rememberRouteResult<DetailResult>(key = "url-result") { result ->
        lastResult = result.toString()
        businessValue = result.get()?.str
    }

    DemoScreen(
        eyebrow = "参数注入",
        title = "参数详情",
        subtitle = "对照入口提供的预期参数。Detail 同时绑定内联和 Owner 拦截器，OwnerDetail 仅绑定 Owner 拦截器；下方显示最终参数。",
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
                title = "实际接收结果",
                body = "由当前 Composition 注册的 Consumer 接收，分别展示完整结果和业务值。",
            ) {
                DemoSectionSpacer()
                DemoBulletRow(label = "result", value = lastResult!!)
                DemoBulletRow(label = "业务值", value = businessValue ?: "无业务值")
            }
        }

        DemoInfoCard(
            title = "继续导航",
            body = "进入结果回传页，返回时观察结果消费。下方分别演示实例 push 和 URL push。",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DemoActionButton(
                    title = "打开结果回传页（实例方式）",
                    description = "使用 rememberRouteResult 绑定 DetailResult。",
                    onClick = {
                        navigator.pushForResult(Router.Result(null), resultTo = detailResultConsumer)
                    },
                )

                DemoActionButton(
                    title = "打开结果回传页（URL 方式）",
                    description = "URL 场景下使用 rememberRouteResult 绑定结果宿主。",
                    onClick = {
                        navigator.pushForResult("xxxx://test/result", resultTo = urlResultConsumer)
                    },
                )
            }
        }
    }
}

data class DetailResult(val str: String)
