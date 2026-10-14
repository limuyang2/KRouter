# Repository Guidelines

## Project Structure & Module Organization

- `lib_router/`: Navigation 3 runtime. Targets: Android, JVM, JS, Wasm, iOS. Sources: `src/commonMain`, `src/androidMain`, `src/jvmMain`, and `src/iosMain`.
- `lib_router_ksp/`: processors/templates. `router-gradle-plugin/`: versioned indices, with TestKit tests in `src/test/kotlin`. Both compile `router-index-protocol/` sources.
- `composeApp/`: shared demo — a "route verification console" home page with five case categories (navigation/params, interceptors, results, back stack, cross-module). `androidApp/`, `desktopApp/`, `iosApp/`, and `webApp/` (JS + Wasm browser) are hosts. Assets include `androidApp/src/main/res/` and `iosApp/iosApp/Assets.xcassets/`.
- `demo-feature-{product,account,shared}/`: KMP route fixtures testing diamond aggregation via `api`. Integration tests: `composeApp/src/jvmTest/` and `composeApp/src/webTest/` (shared by JS/Wasm, Karma + headless Chrome); device tests: `androidApp/src/androidTest/`.
- Runtime tests: `lib_router/src/jvmTest/`. Versions: `gradle/libs.versions.toml`; release version: `ROUTER_VERSION` in `gradle.properties`. Never edit generated outputs.

## Build, Test, and Development Commands

Use the wrapper and JDK 21. Android requires its SDK; iOS requires macOS/Xcode.

- `./gradlew :lib_router:jvmTest`: run runtime regression tests.
- `./gradlew :composeApp:jvmTest`: test generated registration, parameters, and results.
- `./gradlew :router-gradle-plugin:test`: test indices, dependency selection, publication, caches, and KSP compilations.
- `./gradlew :lib_router:compileAndroidMain :composeApp:compileKotlinJvm`: compile Android runtime and JVM demo, including KSP and aggregation.
- `./gradlew :lib_router:compileKotlinIosArm64 :lib_router:compileKotlinIosSimulatorArm64`: check both iOS runtime targets.
- `./gradlew :androidApp:assembleDebug`: build the Android demo APK.
- `./gradlew :androidApp:connectedDebugAndroidTest`: run Compose instrumented tests on a connected device.
- `./gradlew :desktopApp:run`: launch the desktop demo.
- `./gradlew :webApp:jsBrowserDevelopmentRun` / `:webApp:wasmJsBrowserDevelopmentRun`: launch the browser demo; `:composeApp:jsBrowserTest` / `:composeApp:wasmJsBrowserTest` run `src/webTest/` (need Chrome).
- Publishing: artifacts are on Maven Central (`io.github.limuyang2:router`, `router-ksp`, `router-gradle-plugin`, plus two plugin markers). Bump `ROUTER_VERSION` in `gradle.properties`; `./gradlew bundleMavenCentral` builds the locally signed ZIP for manual Central Portal upload — it never uploads.
- `git diff --check`: detect whitespace errors before committing.

## Coding Style & Naming Conventions

Use four-space indentation, PascalCase types, camelCase members, and public KDoc. Keep platform APIs out of `commonMain`. Follow existing formatting; no ktlint/Detekt is configured.

Keep module IDs stable across targets and checkouts. Aggregate indices from selected dependencies; never scan other projects or feed aggregate outputs back into KSP. Keep `PopType` internal. Interceptors inspect resolved `PopRequest` targets and change destinations through `redirect`. Preserve instance identity (`===`), asynchronous target binding, result delivery, and root-page protection.

## Testing Guidelines

Use `kotlin.test` and `kotlinx-coroutines-test`; name files `*Test.kt` and methods after observable behavior. Use controlled dispatchers and gates instead of sleeps. Cover target changes, duplicate operations, result routing, and invalidation when modifying navigation. Restore dispatcher and registry state after tests. Compilation does not replace device tests or UI previews.

`PopNavigationDeviceTest` (androidApp) drives the demo through exact UI labels via `onNodeWithText`; when changing demo copy or page structure, update that test in the same change. `composeApp` routes without `UI()` (e.g. `Router.D/E/F`) are KSP processor fixtures — keep them.

## Commit & Pull Request Guidelines

Use Conventional Commits, typically Chinese subjects: `fix(router): 修复返回目标绑定`. Keep subjects under 50 characters. PRs should explain the trigger, resulting behavior, validation, and API migration requirements; link relevant issues and attach screenshots for visual changes. Update `README.md` and `README_EN.md` together when public APIs change — the two must stay in sync.

## Configuration & Agent Guidance

Keep credentials in local properties or environment variables; never add secrets to tracked files. For library APIs, use Context7: resolve the library ID, then query documentation. Skip it for reviews and business logic.
