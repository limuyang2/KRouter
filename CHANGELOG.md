# Changelog

所有显著变更记录于此。
All notable changes to this project are documented in this file.

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)。
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/).

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
