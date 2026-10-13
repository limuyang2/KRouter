package io.github.limuyang2.router.demo

import androidx.compose.runtime.Composable
import io.github.limuyang2.router.IRouterEntryProvider
import io.github.limuyang2.router.anno.Router
import io.github.limuyang2.router.anno.RouterParam
import io.github.limuyang2.router.demo.interceptor.DetailPopInterceptor
import io.github.limuyang2.router.demo.interceptor.DetailPushInterceptor
import kotlinx.collections.immutable.ImmutableCollection


data class TestData(val a: String = "")

/**
 * @author 李沐阳
 * @date 2025/12/6
 * @description
 */
class Router {


    @Router(host = "test", path = "home")
    class Home : IRouterEntryProvider {
        @Composable
        override fun UI() {
            HomePage()
        }
    }

    @Router
    class Web(private val url: String) : IRouterEntryProvider {
        @Composable
        override fun UI() {
            WebPage(url)
        }
    }

    @Router(
        host = "test",
        path = "detail",
        pushInterceptors = [DetailPushInterceptor::class],
        popInterceptors = [DetailPopInterceptor::class],
    )
    class Detail(
        @param:RouterParam(name = "ids") private val ids: ImmutableCollection<Int>,
        @param:RouterParam(name = "source") private val source: Int = 0,
        @param:RouterParam(name = "names") private val names: TestData = TestData(),
        private val file: List<String>? = emptyList()
    ) :
        IRouterEntryProvider {
        @Composable
        override fun UI() {
            DetailPage(ids, names, source)
        }
    }

    @Router(
        host = "test",
        path = "owner-detail",
    )
    class OwnerDetail(
        @param:RouterParam(name = "ids") private val ids: ImmutableCollection<Int>,
        @param:RouterParam(name = "source") private val source: Int = 0,
        @param:RouterParam(name = "names") private val names: TestData = TestData(),
        private val file: List<String>? = emptyList()
    ) : IRouterEntryProvider {
        @Composable
        override fun UI() {
            DetailPage(ids, names, source)
        }
    }

    @Router(scheme = "xxxx", host = "test", path = "result")
    data class Result(@param:RouterParam(name = "sx") val a: String?) : IRouterEntryProvider {
        @RouterParam(name = "f")
        var c: Float = 0f

        @Composable
        override fun UI() {
            ResultPage()
        }
    }


    @Router(scheme = "xxx", host = "test", path = "d")
    class D(@RouterParam(name = "sx") a: String) : IRouterEntryProvider {

        constructor(n: Int) : this(n.toString())
    }

    @Router(scheme = "e", host = "e", path = "e")
    object E : IRouterEntryProvider


    @Router(scheme = "f", host = "f", path = "f")
    class F(f: String) : IRouterEntryProvider {
        constructor(@RouterParam("xx") m: Int) : this(m.toString())
    }

    @Router(host = "test", path = "pop-bug-a")
    class PopBugA : IRouterEntryProvider {
        @Composable
        override fun UI() {
            PopBugAHomePage()
        }
    }

    @Router(host = "test", path = "pop-bug-b")
    class PopBugB : IRouterEntryProvider {
        @Composable
        override fun UI() {
            PopBugBPage()
        }
    }

    @Router(host = "test", path = "pop-bug-c")
    class PopBugC : IRouterEntryProvider {
        @Composable
        override fun UI() {
            PopBugCPage()
        }
    }

}
