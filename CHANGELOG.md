# Changelog

所有显著变更记录于此。
All notable changes to this project are documented in this file.

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)。
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/lang/zh-CN/).

## [1.3.1] - 2026-11-15

### 新增 / Added

- **受保护页面（身份性根保护）**：`Navigator(start) { protect(...) }` 构造期声明 + `protect()/unprotect()` 运行期调整，指定类型无论在栈内哪个位置都不被移除操作带走。柱模型语义：pop/remove/all 目标选择将其剔除（空则操作无效），backTo/popUpTo/cleanStack 修剪穿透继续、仅其自身豁免保留原位，受保护页在栈顶时 pop 无效（同根页行为），isPopCurrent 目标撞上时降级为普通叠加；根页保护不变，是其在栈底时的特例。附带修复 removeAll 全命中保根判断在剔除受保护页后失效的问题（改为按非保护项计数判断）。
  - **Protected pages (identity-based root protection)**: declare at construction via `Navigator(start) { protect(...) }` and adjust at runtime with `protect()/unprotect()`; the declared classes are never removed from the stack regardless of position. Pillar semantics: pop/remove/all selections exclude them (an empty selection does nothing), backTo/popUpTo/cleanStack trims pass through while sparing them in place, a pop with a protected top does nothing (same as root), and an isPopCurrent targeting a protected page degrades to plain stacking. Root protection is unchanged and remains the stack-bottom special case. Also fixes the removeAll retain-root check that broke after protected pages were excluded (now counted over non-protected entries).
- **Push 新增 `popUpTo` 锚点修剪语义**：一步原子完成「修剪到锚点再压入新页」——成功入栈后保留锚点及其以下页面，移除锚点与新页之间的夹层（如登出门禁 `[Home, A, B] -> [Home, Login]`，登录成功后 `pop()` 自然回 Home，不再撞 cleanStack 连根清或 popCurrent 清不掉夹层的两难）。被移除页面按 `Cancelled` 结算；锚点不在栈中时跳过修剪退化为普通 push；与 `isCleanStack`、`isPopCurrent`、`resultTo`/`onRouteResult` 互斥，组合配置直接拒绝。DSL 提供 `popUpTo<T>()` / 类 / URL / 实例四入口，`asRoot = true` 时锚点之下（含原根）一并移除、锚点提为新栈底（栈成 `[锚点, 新页]`，锚点实例 `===` 保留）；拦截器内经 `newBuilder().popUpTo(...)` 可附加同一语义（登出拦截改写的正路）。
  - **Push adds a `popUpTo` anchor-trimming intent**: one atomic "trim to an anchor, then push" step — after a successful push, the anchor and everything below it are retained while pages between the anchor and the new entry are removed (e.g. a logout gate turning `[Home, A, B]` into `[Home, Login]`, where `pop()` after login returns to Home instead of hitting the cleanStack root-clearing vs. popCurrent leftover dilemma). Removed pages settle as `Cancelled`; a missing anchor falls back to a plain push; mutually exclusive with `isCleanStack`, `isPopCurrent` and `resultTo`/`onRouteResult`, with conflicting combinations rejected up front. The DSL offers `popUpTo<T>()` plus class / URL / instance overloads; `asRoot = true` also clears everything below the anchor (including the original root) and promotes the anchor instance (`===`-retained) to the new stack root, yielding `[anchor, new]`. Interceptors can attach the same intent via `newBuilder().popUpTo(...)`.

## [1.3.0] - 2026-11-15

### 新增 / Added

- **拦截器链可获取所属 Navigator**：`IRouterPushInterceptor.Chain` 与 `IRouterPopInterceptor.Chain` 新增 `navigator()`，返回执行当前操作的 `Navigator`。全局拦截器是进程级单例、被所有 Navigator 共享，多 Navigator（如多窗口）场景下借此区分操作归属哪一个栈，做只读检查（`backStack`、`last()` 埋点、去重、门禁）；不建议在其中重入 push/pop。接口仅由库内实现，现有拦截器源码兼容、二进制不兼容。
  - **Interceptor chains expose the owning Navigator**: `IRouterPushInterceptor.Chain` and `IRouterPopInterceptor.Chain` add `navigator()`, returning the `Navigator` executing the current operation. Global interceptors are process-level singletons shared by every Navigator, so this is how multi-Navigator setups (e.g. multiple windows) tell which stack an operation belongs to for read-only inspection (`backStack`, `last()` for tracking, dedup or gating); reentrant push/pop is discouraged. The interfaces are implemented only inside the library, so existing interceptors stay source-compatible while binaries do not.

## [1.2.0] - 2026-11-14

### 修复 / Fixed

- **启动闪屏**：初始路由此前经异步 push 派发，栈底短暂为空白占位页，首帧组合可能渲染出占位/空白页后闪一下真实页面。现在初始路由在 `Navigator` 构造期间同步解析并种入栈底，首帧读到的就是真实页面。
  - **Startup flash**: the start destination used to be dispatched through an asynchronous push, leaving a blank placeholder at the stack root; the first composition could render the placeholder before the real page flashed in. The start destination is now resolved synchronously and seeded during `Navigator` construction, so the first frame reads a real page.

### 变更 / Changed

- 初始路由是导航栈的初始状态而非一次跳转：不再经过 Push 拦截器，也不派发初始 `BackStackEvent.Push`。
  - The start destination is the stack's initial state, not a navigation: it no longer goes through push interceptors and emits no initial `BackStackEvent.Push`.
- `Navigator.core` 去掉懒加载：providers 聚合、索引合并与初始路由解析全部在构造期间完成，首帧组合零初始化开销，并免掉每次属性访问的同步锁。
  - `Navigator.core` is no longer lazy: provider aggregation, index merging, and start-destination resolution all happen during construction, leaving zero initialization cost for the first composition and removing the synchronization lock on every property access.
- 初始 URL 未注册或非法时，栈底为携带原始 URL 的 `UnknownRoute`（可由 fallback 页面展示），不再停留在空白占位页；传入非法类型从崩溃变为日志加 `UnknownRoute`。
  - An unregistered or malformed start URL now seeds `UnknownRoute` carrying the original URL (renderable by a fallback page) instead of a blank placeholder; an invalid start-destination type logs and seeds `UnknownRoute` instead of crashing.

### 升级说明 / Upgrade notes

- **构造顺序成为硬约束**：必须在 `initRouter` 与模块注册完成之后构造 `Navigator`；此前依赖懒加载在注册前提前创建 `Navigator` 的工程需要调整顺序，违反时构造即刻失败（fail fast）。
  - **Construction order is now a hard contract**: construct `Navigator` only after `initRouter` and module registration. Projects that relied on the lazy core to create a `Navigator` before registering modules must reorder; violations now fail fast at construction.
- 原来通过全局 Push 拦截器重定向初始路由（如登录门禁）的用法不再生效，请改为构造前按条件选择 `startDestination`，或在初始页就位后显式跳转。
  - Redirecting the start destination through a global push interceptor (e.g. a login gate) no longer applies; choose the `startDestination` conditionally before construction, or navigate explicitly once the initial page is in place.
- 运行库、KSP 处理器、Gradle 插件三件套照常同版本升级。
  - Upgrade the runtime, KSP processor, and Gradle plugin together as usual.

## [1.1.0] - 2026-11-07

### 修复 / Fixed

- **AGP 宿主下 `RouterAggregatedRegistry` 在 Android Studio 报红**（纯 Android 项目普遍存在）：插件此前仅将聚合目录注入编译任务 source，IDE 模型中看不到该目录，报「找不到符号」但构建与运行正常，Gradle Sync 与清理缓存均无效。现在聚合目录会额外注册为 *static* variant Kotlin 源，一次 Sync 后 IDE 即可解析生成类。
  - **`RouterAggregatedRegistry` showed red (unresolved) in Android Studio on AGP hosts** (affected every pure-Android consumer): the plugin only fed the aggregate directory into the compile task's sources, so the IDE model never saw it — builds ran fine but Sync and cache invalidation could not clear the error. The directory is now additionally registered as a *static* variant Kotlin source, and a single Sync resolves the generated class.
  - 编译接线（`dependsOn` + `source`）保持不变，仍是权威输入与排序来源 / Compiler wiring (`dependsOn` + `source`) is unchanged and remains the authoritative input and ordering.
  - static 注册不携带任务依赖，不会形成「KSP → 聚合任务」的反向边，不影响 KSP 增量与配置缓存；实测聚合文件进入 KSP 源快照后连续构建稳定收敛（KSP 与聚合任务均 UP-TO-DATE）/ Static registration carries no task dependency, so it can never order KSP after the aggregate task — KSP incremental behavior and the configuration cache are unaffected; consecutive builds settle once the aggregate file joins KSP's source snapshot (both tasks stay UP-TO-DATE).

### 新增 / Added

- 新扩展属性 `router { registerIdeSources = true }`（默认开启）：AGP 宿主将聚合目录注册进 IDE 源模型的开关，遇到特殊工程冲突时可设为 `false`，退回 1.0.x 的仅编译任务接入行为。
  - New extension flag `router { registerIdeSources = true }` (on by default): lets AGP hosts opt out of the IDE source registration and fall back to the compile-only wiring of 1.0.x if a project ever conflicts with it.

### 升级说明 / Upgrade notes

- 无破坏性变更；运行时库、KSP 处理器、Gradle 插件三件套照常同版本升级。KMP / JVM / iOS / JS / Wasm 宿主行为不变（聚合一直通过 KGP `generatedKotlin` 注册，本无此问题）。若此前在工程中手写 `variant.sources.kotlin?.addStaticSourceDirectory(...)` 修复报红，升级后请移除，插件已内置。
  - No breaking changes; upgrade the runtime, KSP processor, and Gradle plugin together as usual. KMP / JVM / iOS / JS / Wasm hosts are unaffected (aggregates were always registered via KGP's `generatedKotlin` there). If you manually added `variant.sources.kotlin?.addStaticSourceDirectory(...)` to work around the IDE error, remove that snippet after upgrading — the plugin handles it now.

## [1.0.1] - 2026-11-06

### 构建 / Build

- 升级 AGP、KSP 及相关构建依赖版本；统一 Gradle 插件依赖的版本来源。
  - Upgraded AGP, KSP, and related build dependencies; unified the version source of Gradle plugin dependencies.

### 文档 / Documentation

- 补充全局拦截器 `priority` 优先级说明；更新 README 安装示例中的 KSP 版本并补全拦截器注解示例；补齐运行库公共 API 的中英双语注释。
  - Documented the global interceptor `priority`; refreshed the KSP version in README setup samples and completed interceptor annotation examples; added bilingual KDoc to the runtime's public API.

## [1.0.0] - 2026-10-14

首个 Maven Central 发布版本。
Initial release on Maven Central.

### 核心能力 / Core features

- 基于 Navigation 3 的多平台路由运行时，覆盖 Android、JVM、iOS、JS、Wasm。
  - Multiplatform routing runtime built on Navigation 3 for Android, JVM, iOS, JS, and Wasm.
- KSP 处理器在编译期生成模块路由注册表与索引，无需运行时扫描。
  - A KSP processor generates per-module route registries and indices at compile time — no runtime scanning.
- Gradle 插件按实际编译依赖选择索引，宿主生成 `RouterAggregatedRegistry` 聚合入口；支持本地工程、Maven 制品与 included build 的同一套索引协议。
  - Gradle plugins select indices from the actual compile graph and generate the `RouterAggregatedRegistry` aggregate entry; local projects, Maven artifacts, and included builds share one index protocol.
- URL 导航、类型化参数与结果回传、全局与页面级 Push/Pop 拦截器、返回目标绑定与根页面保护。
  - URL navigation, typed parameters and results, global and page-scoped push/pop interceptors, pop-target binding, and root-page protection.

[1.1.0]: https://github.com/limuyang2/KRouter/compare/1.0.1...1.1.0
[1.0.1]: https://github.com/limuyang2/KRouter/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/limuyang2/KRouter/releases/tag/1.0.0
