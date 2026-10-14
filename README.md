# KRouter

[![Context7](https://img.shields.io/badge/Context7-AI-5D5FEC.svg)](https://context7.com/limuyang2/krouter)

中文 | [English](README_EN.md)

面向 Kotlin Multiplatform 与 Compose Navigation 3 的路由库。通过注解、KSP 和 Gradle 模块聚合，将 URL、页面实例、参数注入、拦截器与结果回传接入同一套导航 API。

**项目状态：已发布 [1.0.0](https://central.sonatype.com/artifact/io.github.limuyang2/router)（Maven Central）。**

## 项目定位

本项目不是 Vibe Coding 项目。KRouter 以明确的 API 语义、可维护的实现、代码审查和可复现的回归测试为开发基础，重点关注页面实例身份、异步返回目标绑定、结果生命周期与多模块注册等导航行为。

KRouter 提供路由解析、导航栈操作和模块注册能力；页面展示、场景策略及页面状态管理通过 Navigation 3 的 `NavDisplay` 和 entry decorators 接入。

## 功能

- **路由声明**：使用 `@Router` 声明页面，KSP 生成路由信息、实例工厂和页面注册代码。
- **跳转方式**：支持页面实例和 URL，使用 `@RouterParam` 注入构造参数及属性。
- **结果回传**：区分成功、无结果、取消和类型不匹配；Compose 使用 Consumer 接收结果。
- **返回控制**：关闭当前页、回退到指定页、移除匹配页、清栈及根页保护。
- **拦截器**：支持全局、页面声明和 Owner 外置绑定的 Push / Pop 拦截器。
- **多模块**：按宿主实际编译依赖聚合模块索引，处理传递依赖和菱形依赖去重。
- **跨平台**：共享路由 API，目标包括 Android、JVM Desktop、iOS、Kotlin/JS 和 Wasm。

## 目录

- [安装](#安装)
- [AI 辅助](#ai-辅助)
- [快速开始](#快速开始)
- [路由与参数](#路由与参数)
- [导航与返回](#导航与返回)
- [结果回传](#结果回传)
- [拦截器](#拦截器)
- [多模块注册](#多模块注册)
- [运行 Demo](#运行-demo)
- [常见问题](#常见问题)
- [许可证](#许可证)

## 安装

KRouter 已发布到 [Maven Central](https://central.sonatype.com/artifact/io.github.limuyang2/router)，当前版本 1.0.0。运行时库、KSP 处理器和 Gradle 插件随同一版本发布，请保持三者版本一致。

| 构件 | 坐标 | 说明 |
| --- | --- | --- |
| 运行时库 | [io.github.limuyang2:router](https://central.sonatype.com/artifact/io.github.limuyang2/router) | KMP 库，按 target 解析 Android / JVM / iOS / JS / Wasm 变体。 |
| KSP 处理器 | [io.github.limuyang2:router-ksp](https://central.sonatype.com/artifact/io.github.limuyang2/router-ksp) | 生成路由信息、实例工厂和注册代码，每个 target 单独添加。 |
| 宿主插件 | [io.github.limuyang2.router](https://central.sonatype.com/artifact/io.github.limuyang2.router/io.github.limuyang2.router.gradle.plugin) | 聚合依赖索引，生成 `RouterAggregatedRegistry`。 |
| 业务模块插件 | [io.github.limuyang2.router.module](https://central.sonatype.com/artifact/io.github.limuyang2.router.module/io.github.limuyang2.router.module.gradle.plugin) | 生成模块索引。两个插件共用 [router-gradle-plugin](https://central.sonatype.com/artifact/io.github.limuyang2/router-gradle-plugin) 实现。 |

环境要求：JDK 21；Android 目标 compileSdk 37、minSdk 26；需另行应用与 Kotlin 版本匹配的 KSP 插件（仓库使用 Kotlin 2.4.21 + KSP 2.3.10）。

### 1. 配置仓库

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

### 2. 业务模块

```kotlin
plugins {
    id("io.github.limuyang2.router.module") version "1.0.0"
    id("com.google.devtools.ksp") version "<与 Kotlin 匹配的版本>"
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // 使用 api 暴露运行时库，宿主才能聚合本模块及其传递依赖的索引。
                api("io.github.limuyang2:router:1.0.0")
            }
        }
    }
}

// KSP 按 target / compilation 配置，这里以 Android 和 iOS 为例；纯 JVM 模块可直接使用 ksp(...)。
dependencies {
    add("kspAndroid", "io.github.limuyang2:router-ksp:1.0.0")
    add("kspIosArm64", "io.github.limuyang2:router-ksp:1.0.0")
}

// 可选：覆盖默认 moduleId（默认取 group:name）。
router {
    moduleId.set("com.example:feature-help")
}
```

### 3. 宿主模块

```kotlin
plugins {
    id("io.github.limuyang2.router") version "1.0.0"
    id("com.google.devtools.ksp") version "<与 Kotlin 匹配的版本>"
}

dependencies {
    implementation(project(":feature-product"))
    implementation("io.github.limuyang2:router:1.0.0")
    add("kspAndroid", "io.github.limuyang2:router-ksp:1.0.0")
    add("kspIosArm64", "io.github.limuyang2:router-ksp:1.0.0")
}

// 聚合当前编译依赖的模块索引，生成 RouterAggregatedRegistry。
router {
    registryPackage.set("com.example.app.generated")
    registryName.set("RouterAggregatedRegistry")
}
```

宿主插件包含业务模块插件的能力，单模块项目只需应用宿主插件。宿主按实际编译依赖选择索引，规则见[多模块注册](#多模块注册)。

## AI 辅助

KRouter 的文档已收录到 [Context7](https://context7.com/limuyang2/krouter)。在 Cursor、Claude Code、Codex 等集成了 Context7 MCP 的 AI 编程助手中，输入 `use context7` 后再提问（例如“如何用 KRouter 接收页面返回结果”），助手会基于本库最新文档回答，而不是凭记忆猜测 API。

## 快速开始

完成宿主的代码生成与模块聚合配置后，可按以下步骤接入。仓库内的完整配置可参考 [composeApp](composeApp/build.gradle.kts) 和 [业务模块示例](demo-feature-product/build.gradle.kts)。

### 1. 声明页面

路由类实现 `IRouterEntryProvider`，在 `UI()` 中提供界面。

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.limuyang2.router.IRouterEntryProvider
import io.github.limuyang2.router.LocalNavigator
import io.github.limuyang2.router.anno.Router
import io.github.limuyang2.router.anno.RouterParam

@Router(scheme = "demo", host = "app", path = "/home")
class HomeRoute : IRouterEntryProvider {
    @Composable
    override fun UI() {
        val navigator = LocalNavigator.current
        Button(onClick = { navigator.push(UserRoute(42L)) }) {
            Text("打开用户页面")
        }
    }
}

@Router(scheme = "demo", host = "app", path = "/user")
class UserRoute(
    @param:RouterParam("id") val id: Long,
) : IRouterEntryProvider {
    @Composable
    override fun UI() {
        val navigator = LocalNavigator.current
        Column {
            Text("用户 ID：$id")
            Button(onClick = { navigator.pop() }) {
                Text("返回")
            }
        }
    }
}
```

对应的路由 URL 为 `demo://app/home` 和 `demo://app/user?id=42`。

### 2. 初始化并注册模块

在创建和使用 `Navigator` 前初始化，再调用宿主生成的聚合入口：

```kotlin
import io.github.limuyang2.router.initRouter
import io.github.limuyang2.router.generated.RouterAggregatedRegistry

fun initializeNavigation() {
    initRouter {
        defaultScheme("demo")
        isLog(false)
    }
    RouterAggregatedRegistry.registerAll()
}
```

示例使用聚合插件的默认包名。修改 `router.registryPackage` 后，应导入对应包中的生成类。

在平台启动入口调用 `initializeNavigation()`，完成后再显示下文的 `App()`。

`initRouter` 保留首次初始化的配置。未显式声明 scheme 的路由使用 `defaultScheme`，其默认值为 `"route"`。模块注册必须在初始化之后进行。

KMP 的聚合入口按 target 生成，`commonMain` 不能直接引用平台生成类。可以在各平台入口初始化，也可以通过 `expect/actual` 分发；仓库示例见 [DemoRuntime.kt](composeApp/src/commonMain/kotlin/io/github/limuyang2/router/demo/DemoRuntime.kt)。

### 3. 接入 NavDisplay

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.limuyang2.router.LocalNavigator
import io.github.limuyang2.router.Navigator
import io.github.limuyang2.router.rememberRouteResultNavEntryDecorator

@Composable
fun App() {
    val navigator = remember { Navigator(HomeRoute()) }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            backStack = navigator.backStack,
            onBack = { navigator.pop() },
            entryDecorators = listOf(
                rememberRouteResultNavEntryDecorator(),
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider<Any> {
                navigator.pageProviderInstaller.forEach { install ->
                    install(this)
                }
            },
        )
    }
}
```

`LocalNavigator` 提供当前导航器。`rememberRouteResultNavEntryDecorator()` 为页面提供结果接收上下文；使用 `rememberRouteResult` 时必须安装它。其余 decorators 分别处理可保存的页面状态与页面级 ViewModel。

完整宿主示例见 [RouterApp.kt](composeApp/src/commonMain/kotlin/io/github/limuyang2/router/demo/RouterApp.kt)。

## 路由与参数

### URL 与实例

URL 使用 `scheme://host/path` 形式，查询参数作为页面参数传入：

```kotlin
// 在点击事件或业务方法中发起跳转。
navigator.push(UserRoute(id = 42L))
navigator.push("demo://app/user?id=42")
```

`@Router(path = "")` 的页面只生成实例注册，不生成 URL 映射。需要通过 URL 打开的页面应声明非空 path。

`@RouterParam` 可以标记构造参数，也可以标记可变属性：

```kotlin
@Router(scheme = "demo", host = "app", path = "/article")
class ArticleRoute(
    @param:RouterParam("id") val id: Long,
) : IRouterEntryProvider {
    @RouterParam("source")
    var source: String = ""

    @Composable
    override fun UI() {
        Text("文章：$id，来源：$source")
    }
}
```

例如 `demo://app/article?id=7&source=home` 会注入 `id` 和 `source`。手动提供的同名 extras 优先于 URL 查询参数。

### 页面身份

同一个对象不能重复存在于返回栈中，判断依据为 `===`。这一约束始终生效，包括开启 `greenChannel` 或关闭 `preventDuplicates` 时。对象离栈后可以再次入栈。

不同对象即使参数相同也可以入栈。`contentKey()` 标识页面内容作用域；相同 key 会共享该作用域的状态、ViewModel 和结果归属，需要独立状态时应提供不同且稳定的 key。

## 导航与返回

### 跳转配置

```kotlin
navigator.push {
    destination = UserRoute(42L)
    preventDuplicates = true
    isCleanStack = false
    isPopCurrent = false
    greenChannel = false
}
```

| 配置 | 含义 |
| --- | --- |
| `destination` | 页面实例；非空时优先于 `url`。 |
| `url` | 用于解析路由和查询参数的 URL。 |
| `extras` | 额外参数，同名项覆盖 URL 参数。 |
| `preventDuplicates` | 阻止与栈顶页面类型相同的重复跳转。 |
| `isCleanStack` | 成功跳转后移除旧页面，仅保留新页面。 |
| `isPopCurrent` | 成功打开新页面后关闭旧 current。 |
| `greenChannel` | 跳过 Push 拦截器，仍执行目标解析和实例唯一性检查。 |

`isPopCurrent` 是关闭旧页面的操作，新页面不会继承旧页面的结果关系。旧页面被上游等待结果时，该请求会结算为 `Cancelled`；`isPopCurrent = true` 不能同时绑定 `resultTo` 或 `onRouteResult`。

### 返回与移除

| 调用 | 行为 |
| --- | --- |
| `navigator.pop()` | 关闭绑定的当前页，不携带业务结果。 |
| `navigator.pop(value)` | 关闭绑定的当前页并返回业务值。 |
| `navigator.pop { to<HomeRoute>() }` | 保留最近的 HomeRoute，移除其上方绑定的页面。 |
| `navigator.pop { last(UserRoute::class) }` | 移除最近一个 UserRoute，其上方其他页面保留。 |
| `navigator.pop { all(UserRoute::class) }` | 移除所有匹配页面；全部命中时保留原根页。 |

`to` 支持类型、URL 和页面实例；`last` 支持类型、类型集合、URL 和页面实例；`all` 支持类型、类型集合和 URL。按实例选择使用 `===`，URL 选择按路由类型匹配，查询参数不参与页面选择。

`to` 未匹配或目标已经位于栈顶时不执行。仅剩根页时，普通返回不会清空导航栈。

### 异步返回

返回目标在进入拦截器前绑定。拦截器等待期间新增的页面不属于原请求：

```text
[A, B]     B 发起返回，拦截器等待
[A, B, C]  等待期间打开 C
[A, C]     放行后关闭原来绑定的 B
```

任一待移除实例或定向返回的接收页提前离栈，原请求会失效；同一对象重新入栈不会恢复旧请求。重复返回请求也不会继续删除其他页面。

`backStack` 用于界面观察，栈变更应通过 `Navigator` 发起，以保留目标绑定、根页保护和结果结算规则。

## 结果回传

### Compose UI

**Compose UI 禁止使用 `pushForResult(onResult)`、`onRouteResult(block)` 或带 callback 的 `destination`。应使用 `rememberRouteResult` 创建 Consumer，并通过 `resultTo` 绑定。**

以下示例在点击事件中打开编辑页，返回后显示完整结果：

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.limuyang2.router.IRouterEntryProvider
import io.github.limuyang2.router.LocalNavigator
import io.github.limuyang2.router.RouteResult
import io.github.limuyang2.router.anno.Router
import io.github.limuyang2.router.rememberRouteResult

data class UserResult(val saved: Boolean)

@Composable
fun ResultExample() {
    val navigator = LocalNavigator.current
    var feedback by rememberSaveable { mutableStateOf("尚未收到结果") }
    val resultTo = rememberRouteResult<UserResult> { result ->
        feedback = when (result) {
            is RouteResult.Success -> "已保存：${result.value?.saved}"
            RouteResult.NoResult -> "没有返回业务值"
            RouteResult.Cancelled -> "结果请求已取消"
            is RouteResult.TypeMismatch -> "返回类型不匹配"
        }
    }

    Column {
        Text(feedback)
        Button(
            onClick = {
                navigator.pushForResult(EditUserRoute(), resultTo = resultTo)
            },
        ) {
            Text("编辑用户")
        }
    }
}

@Router(scheme = "demo", host = "app", path = "/edit-user")
class EditUserRoute : IRouterEntryProvider {
    @Composable
    override fun UI() {
        val navigator = LocalNavigator.current
        Button(onClick = { navigator.pop(UserResult(saved = true)) }) {
            Text("保存并返回")
        }
    }
}
```

同一个 Consumer 也可用于 URL 跳转，例如在点击事件中调用：

```kotlin
navigator.pushForResult("demo://app/edit-user", resultTo = resultTo)
```

Consumer 保存稳定的接收槽位，普通重组时使用最新闭包，页面离开 Composition 后重新进入时绑定新的接收器。同一 entry 内，key 和结果类型共同确定槽位。

### 结果状态

| 返回或移除行为 | 接收结果 |
| --- | --- |
| `pop(value)` | `RouteResult.Success(value)` |
| `pop(result = null)` | `RouteResult.Success(null)` |
| `pop()` | `RouteResult.NoResult` |
| 等待中的请求因目标页被连带移除而取消 | `RouteResult.Cancelled` |
| 实际返回值与接收端声明类型不兼容 | `RouteResult.TypeMismatch` |

`result.get()` 仅提取成功结果中的业务值，其他状态返回 null；它无法区分 `Success(null)` 与无结果、取消或类型不匹配。

结果默认只回传给打开当前页并等待结果的直接调用方。`A → B → C` 中，C 的结果不会自动穿透给 A；B 如需转发，应接收 C 的结果后再显式返回给 A。接收页在后台时，结果等待它回到前台后消费。

### 定向返回

目标页先注册指定 key 的 Consumer：

```kotlin
// HomeRoute 的 UI 内。
val refreshResult = rememberRouteResult<UserResult>(key = "refresh") { result ->
    // 使用 result 刷新首页状态。
}
```

其他页面可直接将结果投递给 HomeRoute，并关闭解析时位于它上方的页面：

```kotlin
navigator.pop {
    to<HomeRoute, UserResult>(
        result = UserResult(saved = true),
        key = "refresh",
    )
}
```

目标 entry 必须曾注册匹配 key 和结果类型的 Consumer。暂时离开 Composition 的接收器会在返回后重新绑定；未注册过匹配槽位的直接结果会被丢弃。中间页面不会因此收到成功结果。

### 非 Compose 业务代码

普通 callback 保存发起请求时的闭包，适用于非 Compose 业务代码：

```kotlin
fun openEditor(
    navigator: Navigator,
    onResult: (RouteResult<UserResult>) -> Unit,
) {
    navigator.pushForResult(EditUserRoute(), onResult = onResult)
}
```

它不会随页面重组或重建替换闭包，调用方应保证捕获对象在结果派发时仍有效。`@DisallowComposableCalls` 不能阻止捕获外部 Compose 状态，因此不能替代上述 Compose UI 使用约定。

## 拦截器

### Push：改写参数或目标

```kotlin
import io.github.limuyang2.router.IRouterPushInterceptor
import io.github.limuyang2.router.PushResponse

class SourcePushInterceptor : IRouterPushInterceptor {
    override suspend fun intercept(
        chain: IRouterPushInterceptor.Chain,
    ): PushResponse {
        val request = chain.request().newBuilder()
            .setExtra("source", "home")
            .build()
        return chain.proceed(request)
    }
}
```

页面可通过 `@Router(pushInterceptors = [...])` 绑定；全局拦截器通过 `@RouterGlobalPushInterceptor` 声明，Owner 外置绑定通过 `@RouterOwnerPushInterceptor` 声明。Pop 提供对应的注解。优先级数值越小越先执行，同一次操作先处理全局拦截器，再处理页面拦截器。

Push 改写目标类型后会重新执行适用的拦截器链。调用 `chain.interrupt()` 可以中断跳转。

### Pop：检查已绑定的页面

```kotlin
import io.github.limuyang2.router.IRouterPopInterceptor
import io.github.limuyang2.router.PopResponse

class AuditPopInterceptor : IRouterPopInterceptor {
    override suspend fun intercept(
        chain: IRouterPopInterceptor.Chain,
    ): PopResponse {
        val request = chain.request()
        // 根据 request.removeTargets 检查实际将被关闭的页面。
        return chain.proceed()
    }
}
```

| PopRequest 属性 | 含义 |
| --- | --- |
| `operation` | `CURRENT`、`REMOVE` 或 `BACK_TO`。 |
| `removeTargets` | 已绑定的待移除实例，按解析时栈顶到栈底排列。 |
| `backToPage` | `BACK_TO` 保留的目标页，其他操作为 null。 |
| `hasResult` | 是否明确提供了业务值，显式 null 也为 true。 |
| `result` | 原始业务值；需要结合 `hasResult` 判断 null。 |

改变页面选择使用 `redirect`：

```kotlin
return chain.redirect {
    to<HomeRoute>()
}
```

只修改返回值时保留原绑定目标：

```kotlin
return chain.proceed(chain.request().withResult(newValue))
```

`CURRENT` 支持改写结果；`BACK_TO` 需要原请求已配置结果 key 和类型；`REMOVE` 不支持业务结果。每个 Pop 拦截器阶段最多调用一次 `proceed` 或 `redirect`，也可以通过 `chain.interrupt()` 中断。选择条件改变时重新解析目标；相同条件保留原绑定实例。

接口和更多约束见 [运行库 API 注释](lib_router/src/commonMain/kotlin/io/github/limuyang2/router)。

## 多模块注册

KRouter 使用显式模块索引和宿主聚合入口完成跨平台注册。

| 模块角色 | 插件 | 职责 |
| --- | --- | --- |
| 业务模块 | `io.github.limuyang2.router.module` | 为本模块的路由和拦截器提供索引。 |
| 宿主模块 | `io.github.limuyang2.router` | 包含业务模块能力，聚合选中依赖的索引并生成注册入口。 |

每个生产 target / compilation 配置对应的 KSP 处理器。宿主生成 `RouterAggregatedRegistry.registerAll()`，启动时显式注册；各平台使用同样的初始化顺序。

- `moduleId` 标识逻辑模块，同一模块的各 target 和变体保持一致。
- 默认 ID 来自 `project.group:project.name`；group 为空时使用项目名，可通过 `router.moduleId` 覆写。
- 宿主按实际依赖选择索引，不扫描无关子项目，菱形依赖中的同一模块只注册一次。
- 生成的静态入口必须编译可见；宿主显式依赖业务模块，或通过中间模块的 `api` 暴露。
- 模块 ID 冲突、索引损坏或协议不兼容在构建阶段报告。
- 聚合输出按平台生成，不应加入 `commonMain` 或反向加入 KSP 输入。

仓库示例为 `composeApp → 商品 / 账户 → 共享帮助`。商品和账户模块都通过 `api` 暴露帮助模块；宿主聚合四个模块入口，帮助模块只注册一次。

实现细节见 [模块聚合架构文档](docs/router-plugin-architecture.md)。

## 运行 Demo

克隆仓库后使用 Gradle Wrapper。需要 JDK 21；Android Demo 需要对应 SDK，iOS 需要 macOS 和 Xcode，浏览器测试需要 Chrome。当前构建版本以 [版本目录](gradle/libs.versions.toml) 和 [Wrapper 配置](gradle/wrapper/gradle-wrapper.properties) 为准。

| 平台 | 入口 | 运行或构建 |
| --- | --- | --- |
| Android | `androidApp` | `./gradlew :androidApp:assembleDebug`，随后安装 APK。 |
| Desktop | `desktopApp` | `./gradlew :desktopApp:run` |
| iOS | `iosApp` | 使用 Xcode 打开 `iosApp/iosApp.xcodeproj` 并运行。 |
| Wasm | `webApp` | `./gradlew :webApp:wasmJsBrowserDevelopmentRun` |
| Kotlin/JS | `webApp` | `./gradlew :webApp:jsBrowserDevelopmentRun` |

首页“路由验证台”按五类组织用例：跳转与参数、拦截器、结果回传、导航栈和跨模块。每个用例给出输入、预期和实际反馈，页面展示当前导航栈。

未注册 URL 用例在跳转失败回调中打开 Demo 兜底页，保留原始 URL；未注册实例通过 `NavDisplay` 的 fallback 展示兜底内容。清栈用例提供“重建首页”，用于恢复其他验证入口。

## 常见问题

### 为什么结果回传必须安装 decorator？

`rememberRouteResult` 需要由 `rememberRouteResultNavEntryDecorator()` 提供当前 entry 的结果作用域。只提供 `LocalNavigator` 不足以创建结果接收槽位。

### 未注册 URL 为什么没有自动打开 404 页面？

未匹配 URL 会被拒绝入栈，导航栈保持原状。业务侧可在 `routeFailureCallback` 中定义兜底策略。Demo 的 404 页面是宿主定义的处理方式。

### 是否可以把 NavDisplay 的 backStack 当普通列表修改？

请通过 `Navigator` 修改。直接增删列表会绕过实例唯一性、异步目标失效和结果结算规则。

### 路由 URL 会同步到浏览器地址栏吗？

当前 URL 用于应用内部导航，不会自动同步浏览器地址栏或接管浏览器历史记录。

### 找不到生成的 RouterAggregatedRegistry 怎么办？

先确认宿主启用了聚合、目标模块编译可见且配置了对应 target 的 KSP，再构建该 target。KMP 生成入口位于平台 source set，不能直接从 `commonMain` 引用。自定义包名后还需要调整导入。

## 许可证

KRouter 使用 [MIT License](LICENSE)。
