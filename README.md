# Library Router

面向 Compose `navigation3` 的路由框架，支持：

- `@Router` + KSP 自动生成路由表和 `NavDisplay` entry 注册代码
- URL 跳转和对象跳转
- `@RouterParam` 自动注入参数
- 页面级 / 全局 Push、Pop 拦截器
- 页面结果回传
- `URL -> RouteClass` 查询

## 5.1.0 更新（2026-09-07）

- 模块注册改为按实际编译依赖读取版本化索引，支持本地模块、Maven 发布模块和多平台静态注册；新增业务模块插件 `io.github.limuyang2.router.module`。
- `moduleId` 默认从 `group:name` 推导，支持手动覆盖和冲突诊断，生成类名不再随源码路径变化。
- 索引准备和宿主聚合任务支持 Build Cache；生成目录接入 IDE，避免 `RouterAggregatedRegistry` 报红，并保持与 KSP 输入隔离。
- KSP 聚合输出只关联实际参与路由生成的源码，减少无关文件的连带重处理；保留正确的聚合失效和多轮生成行为。
- 异步 Pop 绑定具体页面实例，等待期间进入新页面不会误删；批量移除保留根页面，拦截器通过 `PopRequest` 查看目标、通过 `redirect` 修改请求。

从 5.0.x 升级时，请同时升级 runtime、processor 和 Gradle 插件至 5.1.0，重新构建/发布路由模块，并按下文「Pop API 迁移」调整自定义拦截器。旧生成文件需先清理。

## 重点变化

结果回传需要显式区分两类消费方式：

- `pushForResult`
  关心完整结果语义，最终拿到 `RouteResult<R>`。
- `pushForResult` + `RouteResultConsumerToken`
  Compose 推荐写法。token 由 `rememberRouteResult` 创建；它不直接保存业务 lambda，而是保存稳定 slot，结果派发时使用当前 Composition 注册的最新 consumer。

推荐这样理解：

- 你在 Compose 页面里接收结果：优先用 `pushForResult(..., resultTo = ...)`
- 你在非 Compose 场景里接收结果：用 `pushForResult(...)`
- 如果只关心业务值：对 `RouteResult<R>` 调用 `get()`

核心差异不是 API 形式，而是结果 consumer 的持有方式：

- callback 方式会把回调直接挂在路由请求上
- token 方式只记录 slotId，真正消费结果时取当前 entry 上已注册的最新 consumer

因此：

- 顶层 `push(url/destination)` 只表示纯跳转
- 一切“跳转并等待结果”的业务入口，使用 `pushForResult`
- DSL 中的 `onRouteResult / resultTo` 属于低层入口，只有需要组合多个 push 配置项时再使用

---

## 安装

项目包名前缀统一为 `io.github.limuyang2`。从旧包名迁移时，需要同步更新源码 import、Maven 坐标、Gradle 插件 ID 和自定义 `registryPackage`，清理生成文件后重新构建并发布所有路由模块。Android 示例的 applicationId 和 iOS 示例的 Bundle ID 也使用新前缀。

当前版本 `5.1.0`，包含三个组件：

- runtime：`io.github.limuyang2:router:5.1.0`
- KSP processor：`io.github.limuyang2:router-ksp:5.1.0`
- Gradle 插件：`io.github.limuyang2.router`（宿主）与 `io.github.limuyang2.router.module`（业务模块），版本均为 `5.1.0`。

使用版本目录时，可在 `gradle/libs.versions.toml` 中统一配置：

```toml
[versions]
router = "5.1.0"

[libraries]
router-runtime = { module = "io.github.limuyang2:router", version.ref = "router" }
router-ksp = { module = "io.github.limuyang2:router-ksp", version.ref = "router" }

[plugins]
router-host = { id = "io.github.limuyang2.router", version.ref = "router" }
router-module = { id = "io.github.limuyang2.router.module", version.ref = "router" }
```

运行时依赖使用 `implementation(libs.router.runtime)`；KMP 放在 `commonMain.dependencies`。本版验证的工具链为 Gradle 9.6.1、Kotlin 2.4.0、AGP 9.2.1、KSP 2.3.10，目标包括 Android、JVM、iosArm64 和 iosSimulatorArm64。

KSP 在 KMP 下需要按 target 配置（`kspAndroid` / `kspJvm` / `kspIosArm64` / `kspIosSimulatorArm64` / `kspJs` / `kspWasmJs`），完整接入方式见下文「模块注册（聚合插件）」。

---

## 初始化

在使用 `Navigator` 前先初始化，并注册路由模块：

```kotlin
initRouter {
    defaultScheme("qmstu")
    isLog(true)
}
RouterAggregatedRegistry.registerAll()
```

> `RouterAggregatedRegistry` 由聚合插件生成，Android / Desktop / iOS / Web 各端用法一致，详见下文「模块注册（聚合插件）」。顺序必须是先 `initRouter {}`，再 `registerAll()`。

配置项目前只有两个：

- `defaultScheme`
  当 `@Router` 不写 `scheme` 时使用它，默认值是 `"route"`
- `isLog`
  是否打开库内日志

---

## KSP 配置

如果大多数页面都在同一个 host 下，建议给 KSP 配默认 host：

```kotlin
ksp {
    arg("RouterHost", "home")
}
```

这样 `@Router(host = "")` 时会回落到这里的值。

---

## 声明路由

最基础的路由声明：

```kotlin
@Router(
    scheme = "qmstu",
    host = "home",
    path = "/main",
    desc = "首页"
)
class HomeRoute : IRouterEntryProvider {
    @Composable
    override fun UI() {
        HomePage()
    }
}
```

URL 形态是：

```text
scheme://host/path
```

例如：

```text
qmstu://home/main
```

如果你只需要对象跳转，也可以把路由类当普通类型使用；但要注意：

- `@Router(path = "")` 不会生成 URL 路由映射
- 这类页面仍然可以 `navigator.push(RouteInstance())`
- 但不能依赖 `findClassByUrl` 或 URL 方式命中它

---

## 参数注入

`@RouterParam` 可以标在构造参数上，也可以标在属性上。

```kotlin
@Router(host = "user", path = "/detail")
class UserDetailRoute(
    @param:RouterParam("uid") val uid: Long = 0L
) : IRouterEntryProvider {

    @Composable
    override fun UI() {
        UserDetailPage(uid)
    }
}
```

URL 跳转时：

```text
qmstu://user/detail?uid=10086
```

对应参数会自动注入。

属性注入示例：

```kotlin
@Router(scheme = "xxxx", host = "test", path = "result")
data class ResultRoute(
    @param:RouterParam("sx") val title: String?
) : IRouterEntryProvider {

    @RouterParam("f")
    var progress: Float = 0f

    @Composable
    override fun UI() {
        ResultPage()
    }
}
```

---

## Navigation3 接入

`Navigator` 提供：

- `backStack`
- `backStackEvents`
- `pageProviderInstaller`

典型接法如下：

```kotlin
@Composable
fun App() {
    val navigator = remember { Navigator("qmstu://home/main") }
    val pageEntryProvider = remember { navigator.pageProviderInstaller }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            backStack = navigator.backStack,
            onBack = navigator::pop,
            entryProvider = entryProvider {
                pageEntryProvider.forEach { it(this) }
            }
        )
    }
}
```

如果你在页面里要使用 `rememberRouteValue` / `rememberRouteResult`，要确保当前 Composition 能拿到 `LocalNavigator`。

---

## 跳转 API

### 基础跳转

```kotlin
navigator.push(HomeRoute())
navigator.push("qmstu://home/main")
```

同一个页面对象实例不能在当前返回栈中重复出现（使用 `===` 判断），离栈后可以再次入栈。
此规则不受 `preventDuplicates`、`greenChannel`、`isCleanStack` 或 `isPopCurrent` 影响；检查的是拦截器处理后的最终目标，也适用于 URL 解析得到的单例对象。
重复实例跳转会调用 `routeFailureCallback` 和 `routeCompletedCallback`，不会修改返回栈。不同实例即使参数、`equals` 或 `contentKey` 相同，仍允许入栈；相同 `contentKey` 的状态共享语义保持不变。

### DSL 跳转

```kotlin
navigator.push {
    destination = HomeRoute() // 优先级高于 url
    // url = "qmstu://home/main"

    preventDuplicates = false
    isCleanStack = false
    isPopCurrent = false
    greenChannel = false

    routeBeforeCallback = { }
    routeSuccessCallback = { }
    routeFailureCallback = { }
    routeCompletedCallback = { }
}
```

几个常用开关：

- `preventDuplicates`
  如果目标页面类型已经在栈顶，阻止重复入栈
- `isCleanStack`
  跳转成功后清空旧栈，只保留新页面
- `isPopCurrent`
  新页面入栈后移除当前页。它不是 replace，不会继承旧页的结果关系；因此不能与 `onRouteResult / resultTo` 同时使用
- `greenChannel`
  跳过 Push 拦截器链，直接解析并执行跳转

### `isPopCurrent` 的语义

请把 `isPopCurrent` 理解成：

- `push` 成功后关闭旧 current
- 它是页面生命周期操作，不是 replace
- 新页面不会自动接管旧页面的 request、pending result 或结果消费槽位

因此有两个直接结论：

- 如果旧 current 正被上游等待结果，它在被移除时会按 `Cancelled` 结算
- `isPopCurrent = true` 时，当前页面不能再同时声明 `onRouteResult / resultTo`；库会直接拒绝这次跳转
- 如果需要 `push C` 后立刻移除当前页 `B`，不要让 `B` 同时等待 `C` 的结果；`C` 后续返回值也不会自动穿透给 `A`

---

## 结果回传

### 1. 结果类型声明

结果类型由接收端声明：

- Compose token 入口通过 `rememberRouteResult<R>` 声明。
- callback / DSL 入口通过 `pushForResult<R>` / `onRouteResult<R>` 声明。

这个类型会写入当前 request，用于 `pop(result)` 时做运行时校验。页面类本身不需要额外实现结果类型接口。

### 2. Compose 场景

Compose 页面推荐使用 `rememberRouteResult` 创建 `RouteResultConsumerToken`，再通过 `pushForResult(..., resultTo = token)` 绑定结果。

值语义：

```kotlin
val token = rememberRouteResult<HomeResult> { result ->
    val value: HomeResult? = result.get()
}

navigator.pushForResult(HomeRoute(), resultTo = token)
```

完整结果语义：

```kotlin
val token = rememberRouteResult<HomeResult> { result ->
    when (result) {
        is RouteResult.Success -> Unit
        is RouteResult.NoResult -> Unit
        is RouteResult.Cancelled -> Unit
        is RouteResult.TypeMismatch -> Unit
    }
}

navigator.pushForResult(HomeRoute(), resultTo = token)
```

- `RouteResult.get()` 只保留业务值语义，`NoResult / Cancelled / TypeMismatch` 会返回 `null`。
- `rememberRouteResult` 保留完整 `RouteResult<R>` 语义，适合需要区分成功、无结果、取消、类型不匹配的场景；只关心业务值时直接调用 `result.get()`。
- token 只保存稳定 slot，不保存旧 Composition 的业务 lambda；页面回到前台时，结果会派发给当前 entry 上最新注册的 consumer。
- 使用 `rememberRouteResult` 时，当前 Composition 必须能拿到 `LocalNavigator` 和当前 route entry 上下文。

### 3. 非 Compose 场景

非 Compose 场景使用 callback 形式接收完整 `RouteResult`：

```kotlin
navigator.pushForResult<HomeResult>(HomeRoute()) { result ->
    // result: RouteResult<HomeResult>
}
```

- callback 接收 `RouteResult<R>`，由调用方自行决定是否只处理 `Success`。
- callback 方式会挂在当前 request 上，不具备 Compose token 的 slot 重新绑定能力。

### 4. DSL 结果入口

`Navigator.PushBuilder` 提供两类结果入口：

```kotlin
navigator.push {
    destination = HomeRoute()
    onRouteResult<HomeResult> { result -> }
}

navigator.push {
    destination = HomeRoute()
    resultTo(token)
}
```

- `onRouteResult` / 带 callback 的 `destination`：完整结果语义，接收 `RouteResult<R>`。
- `resultTo` / 带 token 的 `destination`：Compose token 语义，结果投递给当前 entry 上匹配 slot 的 consumer。

业务代码优先使用 `pushForResult` 这类明确入口；只有需要同时配置 URL、extras、拦截器绿色通道、生命周期回调等低层选项时，再使用 DSL 结果入口。

### 5. 当前页返回结果

当前栈顶关闭时，结果会结算给创建该页面的直接 caller。语义如下：

```kotlin
navigator.pop()
navigator.pop(HomeResult(selectedTab = 2))

navigator.pop {
    top(HomeResult(selectedTab = 2))
}
```

- `pop()`
  对应 `RouteResult.NoResult`
- `pop(result = null)` 或 `top(null)`
  对应 `RouteResult.Success(null)`
- `pop(result = value)` 或 `top(value)`
  对应 `RouteResult.Success(value)`

如果一个等待结果的页面在结果返回前就被移除了，未完成请求会收到 `RouteResult.Cancelled`。

如果实际返回值类型和接收端声明的 `R` 不兼容，会收到 `RouteResult.TypeMismatch`。

### 6. 回退到指定页并直接投递结果

`pop { to(..., result, key) }` 表示把结果直接投递给目标页，并移除目标页上方的页面。它类似 Navigation Compose 中的 `getBackStackEntry(A).savedStateHandle.set(...) + popBackStack(A, false)`，但结果投递规则以当前 Router 的 consumer slot 为准。

```kotlin
val token = rememberRouteResult<HomeResult>(key = "refresh") { result ->
    // result: RouteResult<HomeResult>
}

navigator.pop {
    to<HomeRoute, HomeResult>(
        result = HomeResult(selectedTab = 2),
        key = "refresh",
    )
}
```

- result 直接投递给目标页当前 entry 上同 key、同结果类型的 consumer；
- 目标页的当前 entry 必须曾通过 `rememberRouteResult(key = ...)` 注册匹配 consumer；
- 页面暂时离开 Composition 时仅解绑回调，结果会排队，等它回到前台并绑定最新 consumer 后消费；槽位声明随 entry 出栈清理；
- 如果该 entry 从未注册匹配 key/type 的 consumer，result 会被直接丢弃，不会缓存；
- 中间被移除页面的 pending request 不会收到 success，会按 `Cancelled` 或清理处理。
- 直接投递必须携带 `key`，避免多个结果槽位无法区分。

### 7. 结果链路规则

结果回传默认遵循“严格一跳返回”：

- `push(A -> B)` 时，建立的是 “A 等 B” 的 request
- `push(B -> C)` 时，建立的是 “B 等 C” 的 request
- `C` 默认只会把结果回给直接 caller `B`
- 框架不会自动把 `C` 的结果穿透给 `A`

这意味着：

- 如果 `A` 最终想拿到 `C` 的结果，`B` 必须保持存活到收到 `C` 的结果，再由 `B` 显式回传给 `A`
- 如果 `B` 在等待 `C` 结果期间被移除，那么 `A -> B` 这条 request 会结束为 `Cancelled`
- caller 不会在后台立刻消费结果；只有当 caller 重新成为前台页时，pending result 才会真正派发给 callback 或 consumer
- `pop { to(A, result, key) }` 是显式例外：它不通过中间页面 request 链转发，而是直接投递给目标页 A。

---

## Pop API

Pop 分为关闭当前页、回退到指定页、移除指定页三类语义：

```kotlin
navigator.pop {
    top()                  // 关闭当前栈顶
    // to<HomeRoute>()      // 回退到指定页
    // last(HomeRoute::class) // 移除最近一个匹配页
    // all(HomeRoute::class)  // 移除所有匹配页
}
```

- `top`
  绑定拦截前的栈顶实例，放行后关闭它。无参数时返回 `NoResult`；带参数时返回 `Success(value)`，即使 value 为 `null` 也表示明确成功返回。
- `to`
  回退到最近一个匹配页面，它上面的页面都会被移除。带结果时必须使用带 key 的重载，并只投递给目标页匹配 key/type 的当前 consumer；中间页不会自动 success。
- `last`
  移除最近一个匹配页面，但不动它上面的页面。只有被移除页在请求解析时是栈顶，才会按 request 链结算 `NoResult`；否则属于普通移除。按实例选择使用 `===`，不使用 `equals`。
- `all`
  移除所有匹配页面；如果会清空整个返回栈，则保留操作前的根页面（栈底页面）。只剩根页面时不执行移除。
- `greenChannel`
  跳过 Pop 拦截器链，直接执行已解析的页面操作，仍保留根页面保护。

`to` 和 `last/all` 的结果语义不同：`to(..., result, key)` 是直接投递给目标页；`last/all` 不携带业务 result，移除过程中被连带清理的 pending request 会按 `Cancelled` 或清理处理。

### 异步 Pop 的目标绑定

所有 Pop 操作都会在进入拦截器前匹配页面，并绑定到这些实例本次在栈中的生命周期。等待期间新增的页面不加入原操作：

```text
[A,B]   B 发起返回，拦截器等待
[A,B,C] 等待期间进入 C
[A,C]   拦截器放行，关闭原来的 B
```

- B 的主动返回仍向原 caller A 结算 `Success(value)` / `NoResult`，不会因为 B 不再是栈顶而变成 `Cancelled`。A 被 C 覆盖时，结果等 A 回到前台再消费。
- 任一待移除页或 `to(...)` 的接收页提前离栈，整次旧操作失效；同一实例重新入栈不会恢复旧操作。
- 重复发起关闭 B 的请求，至多有一次能移除 B；剩余请求不会继续删除其它页面。
- `to/all/last` 同样只处理解析时绑定的页面。如果等待期间新增 D，D 会保留；`to(..., result, key)` 仍向原目标页投递，原目标页在后台时延迟消费。目标 entry 从未注册匹配 consumer 时仍丢弃直接投递的结果。
- 保证适用于通过 Navigator 发起的栈变更。`backStack` 供 NavDisplay 观察，请使用 Navigator 修改它；直接修改列表会绕过实例限制、请求失效与结果结算逻辑。

---

## 拦截器

### 页面级 Push / Pop 拦截器

```kotlin
class LoginPushInterceptor : IRouterPushInterceptor {
    override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
        val request = chain.request()
        return chain.proceed(request)
    }
}

class AuditPopInterceptor : IRouterPopInterceptor {
    override suspend fun intercept(chain: IRouterPopInterceptor.Chain): PopResponse {
        val request = chain.request()
        // request.operation：CURRENT / REMOVE / BACK_TO
        // request.removeTargets：已匹配的页面，按栈顶到栈底排列
        // request.backToPage：BACK_TO 要保留的接收页，其它操作为 null
        return chain.proceed()
    }
}

@Router(
    host = "home",
    path = "/main",
    pushInterceptors = [LoginPushInterceptor::class],
    pushInterceptorPriorities = [0],
    popInterceptors = [AuditPopInterceptor::class],
    popInterceptorPriorities = [0]
)
class HomeRoute : IRouterEntryProvider
```

### 全局拦截器

```kotlin
@RouterGlobalPushInterceptor(priority = 0)
class GlobalPushInterceptor : IRouterPushInterceptor {
    override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
        return chain.proceed(chain.request())
    }
}

@RouterGlobalPopInterceptor(priority = 0)
class GlobalPopInterceptor : IRouterPopInterceptor {
    override suspend fun intercept(chain: IRouterPopInterceptor.Chain): PopResponse {
        return chain.proceed()
    }
}
```

### 拦截器里改写请求

Push 拦截器可以基于 `request.newBuilder()` 改写目标、URL 或 extras：

```kotlin
class DetailPushInterceptor : IRouterPushInterceptor {
    override suspend fun intercept(chain: IRouterPushInterceptor.Chain): PushResponse {
        val newRequest = chain.request().newBuilder()
            .setExtra("source", 1)
            .build()

        return chain.proceed(newRequest)
    }
}
```

也可以中断：

```kotlin
return chain.interrupt()
```

### Pop 拦截器修改目标或返回值

PopRequest 是 Core 解析后的请求，拦截器通过 `chain.request()` 获取。各属性含义如下：

| 属性 | 含义 |
| --- | --- |
| `operation` | 操作分类：`CURRENT` 对应普通返回，`REMOVE` 对应 `last/all`，`BACK_TO` 对应 `to`。 |
| `removeTargets` | 本次准备关闭的页面实例，按解析时从栈顶到栈底排列，等待期间新增的页面不加入。 |
| `backToPage` | `to(...)` 要保留并回退到的页面；其它操作为 null。它不是每次操作后的栈顶，也不是普通 `pop(result)` 的结果接收页。 |
| `hasResult` | 是否明确提供了业务返回值。没有提供值为 false，明确返回 null 也为 true。它不表示操作已经成功。 |
| `result` | 原始业务返回值，例如商品 ID、编辑后的数据，不是 `RouteResult` 状态对象。为 null 时结合 `hasResult` 判断。 |

例如栈为 `[A, B, C]`，下面每行分别表示独立发起一次请求：

| 调用 | `removeTargets` | `backToPage` | `hasResult` | `result` |
| --- | --- | --- | --- | --- |
| `pop()` | `[C]` | null | false | null |
| `pop(42)` | `[C]` | null | true | 42 |
| `pop(result = null)` | `[C]` | null | true | null |
| `pop { to(A) }` | `[C, B]` | A | false | null |
| `pop { to(A, 42, "refresh") }` | `[C, B]` | A | true | 42 |
| `pop { last(B) }` | `[B]` | null | false | null |

普通 `pop(42)` 的值交给最初打开 C 并等待它返回结果的页面；`to(A, 42, "refresh")` 的值直接交给 A 上匹配 key 和结果类型的 consumer。结果在页面移除后结算，接收页回到前台再消费。拦截器读取 `result` 不会触发投递；普通返回没有对应的等待请求，或直接投递没有匹配 consumer 时，不会送达业务值。

改变页面选择条件使用 `redirect`，DSL 与 `navigator.pop { ... }` 共用，但没有跳过拦截器的 `greenChannel` 开关：

```kotlin
return chain.redirect {
    last(otherPage)
    // to<HomeRoute>()
    // all(DetailRoute::class)
}
```

条件改变后，Core 根据当前栈重新匹配页面，并重新执行全局及新目标页的拦截器。全局拦截器可能执行多次，应根据当前请求判断是否还需要重定向；超过 16 次重定向会中止操作。新条件没有匹配页面时不执行移除。

选择条件相同则继续当前链，沿用原先绑定的实例，不会因为当前栈顶改变而重新选页。例如等待期间 `redirect { top() }` 仍关闭原先绑定的 B；如果明确要改为删除 C，使用 `redirect { last(c) }`。

仅修改返回值可以保留原目标：

```kotlin
return chain.proceed(chain.request().withResult(newValue))
```

`withResult(null)` 表示明确返回 null。`CURRENT` 支持修改结果；`BACK_TO` 必须原先已指定 key/type；`REMOVE` 不支持业务返回值。每个拦截器只能调用一次 `proceed` 或 `redirect`，或直接返回 `chain.interrupt()`。

### Pop API 迁移

`PopType`、`PopResult` 已收为内部类型，`PopRequest.newBuilder().popType(...)` 和直接构造 `PopResponse(...)` 不再开放。已有自定义 Pop 拦截器需要迁移并重新编译：

- 读取 `request.popType` 改为读取操作分类及已匹配的页面。
- 修改 `popType` 改为 `chain.redirect { ... }`；修改返回值使用 `request.withResult(...)`。
- 放行使用 `chain.proceed()`；中断使用 `chain.interrupt()`。

`chain.proceed(chain.request())` 仍可使用，Navigator 原有的 `top/to/last/all` 调用方式保持不变。

---

## 模块注册（聚合插件）

路由模块的注册通过 Gradle 聚合插件统一处理，**Android / Desktop / iOS / Web 各端用法完全一致**，不再依赖 JVM `ServiceLoader`，也无需在 iOS 侧手动注册。

> 以下接入方式适用于 5.1.0。升级时需要同时更新 Gradle 插件和 router-ksp；旧版 5.0.x 不支持 module 插件及此索引协议。

整体思路：

1. 声明路由或拦截器的业务模块应用 `io.github.limuyang2.router.module`，配置各平台的 KSP processor；`moduleId` 默认自动推导，也可手动覆盖。
2. KSP 生成 `ModuleRouterProvider_*`、`ModuleRouterRegistry_*` 和版本化 JSON 索引。模块身份来自 `moduleId`，不包含源码路径、版本号或 target 名。
3. 宿主应用 `io.github.limuyang2.router`，按实际编译依赖选择本地或 Maven 索引，生成 `RouterAggregatedRegistry`。普通依赖和无关子项目不参与聚合。
4. 宿主启动时调用一次 `RouterAggregatedRegistry.registerAll()`。宿主插件已包含 module 插件的能力。

### 1. 业务模块：启用 module 插件和 KSP

每个业务模块都要单独配置。以下 target 名应与模块实际声明一致；例如 `jvm("desktop")` 使用 `kspDesktop`：

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.router.module)
}

dependencies {
    add("kspAndroid", libs.router.ksp)
    add("kspJvm", libs.router.ksp)
    add("kspIosArm64", libs.router.ksp)
    add("kspIosSimulatorArm64", libs.router.ksp)
    add("kspJs", libs.router.ksp)
    add("kspWasmJs", libs.router.ksp)
}
```

运行时库依赖仍按平台或 commonMain 配置。普通 Android 模块使用 `ksp(libs.router.ksp)`。无路由的普通库无需应用 module 插件；已经应用插件但没有路由的 compilation 会提供空索引。

`moduleId` 是可选配置，默认使用 `project.group:project.name`；`group` 为空时仅使用 `project.name`。例如 `group = "com.example"`、项目名为 `feature-home`，得到 `com.example:feature-home`。读取的是 Gradle 项目的最终配置，不读取单独修改的 Maven publication 坐标。

同一逻辑模块的各平台/变体共用此 ID。不同项目或组合构建可能推导出相同 ID；当它们同时进入宿主聚合时，构建会报错并列出双方来源，提示在其中一个模块手动覆盖，然后重新构建或发布该模块：

```kotlin
router {
    // 可选：解决身份冲突，或在项目/group 改名后继续保持原来的模块身份。
    moduleId.set("com.example:feature-home")
}
```

显式值优先于默认推导。ID 需以 ASCII 字母或数字开头，仅包含字母、数字、`_`、`.`、`:`、`-`；无法推导出合法 ID 时也会提示手动覆盖。使用默认值时，在 `settings.gradle(.kts)` 中固定 `rootProject.name`，避免 Gradle 依据 checkout 目录生成不同的项目名或默认 group；修改项目名或 group 会改变默认 ID。

模块 provider/registry 类名的后缀由 `moduleId` 的 SHA-256 前 24 位确定。需要修改模块生成包名时使用 KSP 参数；它与宿主的聚合包名互相独立：

```kotlin
ksp {
    arg("router.registryPackage", "com.example.feature_home.generated")
}
```

### 2. 宿主模块：应用聚合插件

宿主同样需要 KSP 插件和各 compilation 的 processor 依赖：

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.router.host) // io.github.limuyang2.router
}

router {
    registryPackage.set("com.example.generated")
    registryName.set("RouterAggregatedRegistry")
}
```

插件按实际 main compilation 或 Android variant 注册任务，支持自定义 JVM target、Android Application/Library（含 flavor）、Android KMP、Kotlin Native、JS 和 Wasm；当前验证的 Native 目标为 iosArm64、iosSimulatorArm64。测试 compilation 暂不支持。本仓库通过 `includeBuild("router-gradle-plugin")` 使用本地插件；外部项目需集中配置配套版本。

宿主必须能在编译期访问需要注册的模块。对被 `implementation` 隐藏或仅通过 `runtimeOnly` 引入的 Router 模块，JVM/Android 构建会报错，提示在宿主显式添加依赖或由中间模块通过 `api` 暴露。Native 按 compilation 的依赖图聚合，不承诺发现图外的隐藏依赖。

发布路由库时，应保留插件提供的索引产物和 Gradle Module Metadata（`.module`），并同时发布对应的平台代码。旧 JAR/AAR/KLIB 没有索引标记，无法自动发现，需要重新发布或显式调用旧模块的注册入口。

首次迁移请用 `./gradlew clean` 清除旧的路径哈希命名产物，再重新构建/发布所有路由模块。不要沿用旧的手工 KSP 目录扫描、跨子项目任务依赖或把聚合目录添加到 KSP 输入。详细设计和验收范围见 [插件架构文档](docs/router-plugin-architecture.md)。

### 3. 初始化

在宿主启动时，先 `initRouter {}`，再调用聚合 registry：

```kotlin
initRouter {
    defaultScheme("test")
    isLog(true)
}
RouterAggregatedRegistry.registerAll()
```

注意顺序：必须先 `initRouter {}`（`registerModuleRouters` 内部会校验初始化状态），再 `registerAll()`。

由于 `RouterAggregatedRegistry` 由聚合插件按 target 生成在平台 source set 中，commonMain 无法直接引用。推荐用 `expect/actual` 在各平台调用，或直接在各平台入口（Android `Application.onCreate` / Desktop `main` / iOS SwiftUI `init` / Web `main`）分别调用。本仓库 demo 在 `initDemoRouter()` 内通过 `expect fun registerAggregatedRouters()` 统一分发，各端入口都只调一次 `initDemoRouter()`。

Kotlin JVM/KMP 宿主的聚合目录通过 KGP 2.4 的 `generatedKotlin` 注册到 IDE。升级插件后请同步 Gradle；首次使用还需构建对应 target 以生成入口，例如 `./gradlew :composeApp:compileKotlinIosArm64`。无需手动标记源码目录，也不要将聚合输出加入 `commonMain` 或 KSP 输入。

### 4. 仓库多模块示例

三个示例模块均使用 module 插件，支持 Android、JVM、iosArm64、iosSimulatorArm64、js 和 wasmJs：

| 模块 | 页面与入口 | moduleId |
| --- | --- | --- |
| `demo-feature-product` | 商品详情：`test://modules/product?id=42` | 默认 `library-router:demo-feature-product` |
| `demo-feature-account` | 账户中心：`AccountPage("小明")` 或 `test://modules/account?name=guest` | 默认 `library-router:demo-feature-account` |
| `demo-feature-shared` | 帮助中心：`HelpPage(...)` 或 `test://modules/help?topic=account` | 手动覆盖为 `io.github.limuyang2:demo-help` |

`composeApp` 只直接依赖商品和账户模块；两者都通过 `api(project(":demo-feature-shared"))` 暴露帮助模块，形成菱形依赖。宿主按实际依赖聚合出四个注册入口（包含自身），帮助模块只注册一次。三个业务模块都不依赖宿主，也不手动调用注册类。

Demo 首页新增“打开商品详情”和“打开账户中心”。商品通过实例打开帮助页，账户通过 URL 打开同一个帮助页；点击“问题已解决”后，以 `HelpResult` 向各自的调用页面回传结果。

验证命令：

```bash
./gradlew :composeApp:jvmTest
./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinIosSimulatorArm64
./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.limuyang2.router.demo.PopNavigationDeviceTest
```

JVM 集成测试使用真实 KSP 产物检查路由映射、参数注入、模块去重和结果回传；真机测试从首页点击入口，覆盖跨模块跳转、Activity 重建、结果只消费一次及系统返回。

---

## 常用查询

```kotlin
navigator.last()
navigator.findClassByUrl("test://home/main")
```

含义分别是：

- `last()`
  当前栈顶实例，但不移除
- `findClassByUrl(url)`
  查 URL 对应的路由类

---

## 使用建议

1. 先把 `initRouter {}` 和 `Navigator` 接入跑通，再接参数注入和拦截器。
2. 新的 Compose 页面，结果回传默认使用 `pushForResult(..., resultTo = rememberRouteResult(...))`。
3. 非 Compose 接收结果时用 `pushForResult(...)`，回调接收 `RouteResult<R>`。
4. DSL 结果入口只在需要组合低层 push 配置时使用；普通业务结果回传优先选择 `pushForResult`。
5. 如果一个页面需要被 URL 打开，记得声明 `@Router(path = ...)`。
6. `moduleId` 默认自动推导，冲突或需要固定身份时手动覆盖；宿主应用 `io.github.limuyang2.router` 聚合插件后，各端都只需调用一次 `RouterAggregatedRegistry.registerAll()`。

## Web（Kotlin/JS 与 Wasm）

运行时、KSP 模块索引和宿主聚合同时支持 `js` 与 `wasmJs`。业务模块与宿主需要声明相同的 Web target，并分别配置 `kspJs`、`kspWasmJs`；模块 ID 在所有 target 间保持一致。运行时发布产物为 `router-js`、`router-wasm-js`，KMP 使用 `io.github.limuyang2:router` 自动选择平台产物。

`webApp` 是浏览器宿主，复用 `composeApp` 和三个 feature 模块，入口先调用 `initDemoRouter()`，再通过 `ComposeViewport { RouterApp() }` 显示页面。JS/Wasm 共享 `webMain`；各 target 独立生成注册入口，使用同一套 URL 跳转、参数注入、拦截器与结果回传 API。

```bash
# 启动 Wasm 或 JS 开发服务器
./gradlew :webApp:wasmJsBrowserDevelopmentRun
./gradlew :webApp:jsBrowserDevelopmentRun

# 生成可部署的静态产物
./gradlew :webApp:wasmJsBrowserDistribution :webApp:jsBrowserDistribution

# 浏览器回归测试，需要安装 Chrome（可通过 CHROME_BIN 指定路径）
./gradlew :lib_router:jsBrowserTest :lib_router:wasmJsBrowserTest :composeApp:jsBrowserTest :composeApp:wasmJsBrowserTest
```

两种 target 的静态产物分别位于 `webApp/build/dist/wasmJs/productionExecutable/` 和 `webApp/build/dist/js/productionExecutable/`。路由 URL 用于应用内部导航，当前不会自动同步浏览器地址栏或接管浏览器历史记录。
