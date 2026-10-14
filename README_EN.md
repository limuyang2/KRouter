# KRouter

[中文](README.md) | English

A routing library for Kotlin Multiplatform and Compose Navigation 3. Through annotations, KSP, and Gradle module aggregation, it brings URLs, page instances, parameter injection, interceptors, and result delivery into a single navigation API.

**Status: [1.0.0](https://central.sonatype.com/artifact/io.github.limuyang2/router) released on Maven Central.**

## About this project

This is not a vibe-coding project. KRouter is built on explicit API semantics, a maintainable implementation, code review, and reproducible regression tests, with a focus on navigation behaviors such as page instance identity, asynchronous back-target binding, result lifecycles, and multi-module registration.

KRouter provides route resolution, navigation-stack operations, and module registration. Page presentation, scene strategies, and page state management integrate through Navigation 3's `NavDisplay` and entry decorators.

## Features

- **Route declaration**: Declare pages with `@Router`; KSP generates route metadata, instance factories, and page registration code.
- **Navigation**: Supports page instances and URLs; `@RouterParam` injects constructor parameters and properties.
- **Result delivery**: Distinguishes success, no result, cancellation, and type mismatch; Compose receives results through a Consumer.
- **Back control**: Close the current page, roll back to a specific page, remove matching pages, clear the stack, with root-page protection.
- **Interceptors**: Global, page-declared, and owner-bound Push / Pop interceptors.
- **Multi-module**: Aggregates module indices from the host's actual compile dependencies, handling transitive dependencies and diamond-dependency deduplication.
- **Cross-platform**: A shared routing API targeting Android, JVM Desktop, iOS, Kotlin/JS, and Wasm.

## Contents

- [Installation](#installation)
- [Quick start](#quick-start)
- [Routes and parameters](#routes-and-parameters)
- [Navigation and back](#navigation-and-back)
- [Result delivery](#result-delivery)
- [Interceptors](#interceptors)
- [Multi-module registration](#multi-module-registration)
- [Running the demo](#running-the-demo)
- [FAQ](#faq)
- [License](#license)

## Installation

KRouter is published to [Maven Central](https://central.sonatype.com/artifact/io.github.limuyang2/router); the current version is 1.0.0. The runtime library, KSP processor, and Gradle plugins ship with the same version — keep the three in sync.

| Artifact | Coordinates | Notes |
| --- | --- | --- |
| Runtime library | [io.github.limuyang2:router](https://central.sonatype.com/artifact/io.github.limuyang2/router) | KMP library; resolves Android / JVM / iOS / JS / Wasm variants per target. |
| KSP processor | [io.github.limuyang2:router-ksp](https://central.sonatype.com/artifact/io.github.limuyang2/router-ksp) | Generates route metadata, instance factories, and registration code; add per target. |
| Host plugin | [io.github.limuyang2.router](https://central.sonatype.com/artifact/io.github.limuyang2.router/io.github.limuyang2.router.gradle.plugin) | Aggregates dependency indices and generates `RouterAggregatedRegistry`. |
| Feature-module plugin | [io.github.limuyang2.router.module](https://central.sonatype.com/artifact/io.github.limuyang2.router.module/io.github.limuyang2.router.module.gradle.plugin) | Produces module indices. Both plugins share the [router-gradle-plugin](https://central.sonatype.com/artifact/io.github.limuyang2/router-gradle-plugin) implementation. |

Requirements: JDK 21; Android targets need compileSdk 37 and minSdk 26; apply a KSP plugin matching your Kotlin version (this repository uses Kotlin 2.4.21 + KSP 2.3.10).

### 1. Configure repositories

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

### 2. Feature modules

```kotlin
plugins {
    id("io.github.limuyang2.router.module") version "1.0.0"
    id("com.google.devtools.ksp") version "<version matching your Kotlin>"
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // Expose the runtime library with api so the host can aggregate
                // this module's index and its transitive dependencies.
                api("io.github.limuyang2:router:1.0.0")
            }
        }
    }
}

// Configure KSP per target / compilation; Android and iOS shown here.
// Pure JVM modules can use ksp(...) directly.
dependencies {
    add("kspAndroid", "io.github.limuyang2:router-ksp:1.0.0")
    add("kspIosArm64", "io.github.limuyang2:router-ksp:1.0.0")
}

// Optional: override the default moduleId (defaults to group:name).
router {
    moduleId.set("com.example:feature-help")
}
```

### 3. Host module

```kotlin
plugins {
    id("io.github.limuyang2.router") version "1.0.0"
    id("com.google.devtools.ksp") version "<version matching your Kotlin>"
}

dependencies {
    implementation(project(":feature-product"))
    implementation("io.github.limuyang2:router:1.0.0")
    add("kspAndroid", "io.github.limuyang2:router-ksp:1.0.0")
    add("kspIosArm64", "io.github.limuyang2:router-ksp:1.0.0")
}

// Aggregates the indices of current compile dependencies into RouterAggregatedRegistry.
router {
    registryPackage.set("com.example.app.generated")
    registryName.set("RouterAggregatedRegistry")
}
```

The host plugin includes the feature-module plugin's capabilities, so a single-module project only needs the host plugin. The host selects indices from its actual compile dependencies — see [multi-module registration](#multi-module-registration).

## Quick start

Once code generation and module aggregation are configured on the host, integrate as follows. For the full in-repo configuration, see [composeApp](composeApp/build.gradle.kts) and the [feature-module example](demo-feature-product/build.gradle.kts).

### 1. Declare pages

Route classes implement `IRouterEntryProvider` and provide their UI in `UI()`.

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
            Text("Open user page")
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
            Text("User ID: $id")
            Button(onClick = { navigator.pop() }) {
                Text("Back")
            }
        }
    }
}
```

The corresponding route URLs are `demo://app/home` and `demo://app/user?id=42`.

### 2. Initialize and register modules

Initialize before creating or using a `Navigator`, then call the host's generated aggregate entry point:

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

The example uses the aggregation plugin's default package. If you change `router.registryPackage`, import the generated class from the corresponding package.

Call `initializeNavigation()` in each platform's launch entry, and only show the `App()` below afterwards.

`initRouter` keeps the first initialization's configuration. Routes without an explicit scheme use `defaultScheme`, whose default value is `"route"`. Module registration must happen after initialization.

The KMP aggregate entry point is generated per target, so `commonMain` cannot reference the platform-generated class directly. Initialize in each platform entry point, or dispatch through `expect/actual`; see [DemoRuntime.kt](composeApp/src/commonMain/kotlin/io/github/limuyang2/router/demo/DemoRuntime.kt) for the in-repo example.

### 3. Wire up NavDisplay

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

`LocalNavigator` provides the current navigator. `rememberRouteResultNavEntryDecorator()` provides the result-receiving context for pages; it must be installed when using `rememberRouteResult`. The remaining decorators handle saveable page state and page-scoped ViewModels respectively.

See [RouterApp.kt](composeApp/src/commonMain/kotlin/io/github/limuyang2/router/demo/RouterApp.kt) for the complete host example.

## Routes and parameters

### URLs and instances

URLs use the `scheme://host/path` form; query parameters are passed to the page:

```kotlin
// Trigger navigation from a click handler or business method.
navigator.push(UserRoute(id = 42L))
navigator.push("demo://app/user?id=42")
```

A page declared with `@Router(path = "")` only generates instance registration, without a URL mapping. Pages that must open via URL should declare a non-empty path.

`@RouterParam` can mark constructor parameters as well as mutable properties:

```kotlin
@Router(scheme = "demo", host = "app", path = "/article")
class ArticleRoute(
    @param:RouterParam("id") val id: Long,
) : IRouterEntryProvider {
    @RouterParam("source")
    var source: String = ""

    @Composable
    override fun UI() {
        Text("Article: $id, source: $source")
    }
}
```

For example, `demo://app/article?id=7&source=home` injects both `id` and `source`. Manually provided extras with the same name take precedence over URL query parameters.

### Page identity

The same object cannot exist in the back stack twice; identity is determined by `===`. This constraint always applies, including when `greenChannel` is enabled or `preventDuplicates` is disabled. An object can enter the stack again after leaving it.

Different objects may enter the stack even with identical parameters. `contentKey()` identifies a page's content scope; equal keys share that scope's state, ViewModels, and result ownership — provide distinct, stable keys when independent state is required.

## Navigation and back

### Push configuration

```kotlin
navigator.push {
    destination = UserRoute(42L)
    preventDuplicates = true
    isCleanStack = false
    isPopCurrent = false
    greenChannel = false
}
```

| Option | Meaning |
| --- | --- |
| `destination` | The page instance; takes precedence over `url` when non-null. |
| `url` | URL used to resolve the route and query parameters. |
| `extras` | Extra parameters; same-named entries override URL parameters. |
| `preventDuplicates` | Blocks a duplicate push whose target type matches the top of the stack. |
| `isCleanStack` | Removes old pages after a successful push, keeping only the new page. |
| `isPopCurrent` | Closes the old current page after the new page opens successfully. |
| `greenChannel` | Skips Push interceptors; target resolution and instance-identity checks still run. |

`isPopCurrent` closes the old page, and the new page does not inherit its result relationships. If the old page was being awaited for a result, that request settles as `Cancelled`; `isPopCurrent = true` cannot be combined with `resultTo` or `onRouteResult`.

### Back and removal

| Call | Behavior |
| --- | --- |
| `navigator.pop()` | Closes the bound current page without a business result. |
| `navigator.pop(value)` | Closes the bound current page and returns a business value. |
| `navigator.pop { to<HomeRoute>() }` | Keeps the most recent HomeRoute and removes the pages bound above it. |
| `navigator.pop { last(UserRoute::class) }` | Removes the most recent UserRoute; pages above it are kept. |
| `navigator.pop { all(UserRoute::class) }` | Removes all matching pages; if everything matches, the original root page is kept. |

`to` accepts types, URLs, and page instances; `last` accepts types, type collections, URLs, and instances; `all` accepts types, type collections, and URLs. Instance selection uses `===`; URL selection matches by route type, and query parameters do not participate in page selection.

`to` does nothing when unmatched or when the target is already on top. When only the root page remains, a normal back does not empty the navigation stack.

### Asynchronous back

Back targets are bound before interceptors run. Pages added while an interceptor waits do not belong to the original request:

```text
[A, B]     B requests back; the interceptor waits
[A, B, C]  C is opened while waiting
[A, C]     Once allowed through, the originally bound B is removed
```

If any instance pending removal — or the receiving page of a directed back — leaves the stack early, the original request is invalidated; re-entering the same object does not revive the old request. Duplicate back requests do not remove additional pages either.

`backStack` is for UI observation; stack changes should go through `Navigator` to preserve target binding, root-page protection, and result settlement rules.

## Result delivery

### Compose UI

**In Compose UI, do not use `pushForResult(onResult)`, `onRouteResult(block)`, or a `destination` carrying a callback. Create a Consumer with `rememberRouteResult` and bind it with `resultTo`.**

The example below opens an edit page from a click handler and shows the full result on return:

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
    var feedback by rememberSaveable { mutableStateOf("No result yet") }
    val resultTo = rememberRouteResult<UserResult> { result ->
        feedback = when (result) {
            is RouteResult.Success -> "Saved: ${result.value?.saved}"
            RouteResult.NoResult -> "No business value returned"
            RouteResult.Cancelled -> "Result request cancelled"
            is RouteResult.TypeMismatch -> "Returned type mismatch"
        }
    }

    Column {
        Text(feedback)
        Button(
            onClick = {
                navigator.pushForResult(EditUserRoute(), resultTo = resultTo)
            },
        ) {
            Text("Edit user")
        }
    }
}

@Router(scheme = "demo", host = "app", path = "/edit-user")
class EditUserRoute : IRouterEntryProvider {
    @Composable
    override fun UI() {
        val navigator = LocalNavigator.current
        Button(onClick = { navigator.pop(UserResult(saved = true)) }) {
            Text("Save and return")
        }
    }
}
```

The same Consumer also works with URL navigation — call it from a click handler:

```kotlin
navigator.pushForResult("demo://app/edit-user", resultTo = resultTo)
```

A Consumer holds a stable receiving slot: during ordinary recomposition the latest closure is used, and a page leaving and re-entering Composition binds a new receiver. Within one entry, the key and result type together identify the slot.

### Result states

| Back or removal behavior | Received result |
| --- | --- |
| `pop(value)` | `RouteResult.Success(value)` |
| `pop(result = null)` | `RouteResult.Success(null)` |
| `pop()` | `RouteResult.NoResult` |
| A pending request cancelled because the target page was removed alongside others | `RouteResult.Cancelled` |
| The actual value is incompatible with the receiver's declared type | `RouteResult.TypeMismatch` |

`result.get()` extracts only the business value of a success; every other state returns null. It cannot distinguish `Success(null)` from no result, cancellation, or type mismatch.

By default, a result returns only to the direct caller that opened the current page and is awaiting it. In `A → B → C`, C's result does not automatically pass through to A; if B needs to forward it, B should receive C's result and explicitly return it to A. While the receiving page is in the background, the result waits for it to return to the foreground before being consumed.

### Directed result return

The target page first registers a Consumer with the chosen key:

```kotlin
// Inside HomeRoute's UI.
val refreshResult = rememberRouteResult<UserResult>(key = "refresh") { result ->
    // Use result to refresh the home page state.
}
```

Other pages can deliver a result directly to HomeRoute and close the pages resolved above it:

```kotlin
navigator.pop {
    to<HomeRoute, UserResult>(
        result = UserResult(saved = true),
        key = "refresh",
    )
}
```

The target entry must have registered a Consumer matching the key and result type. A receiver that temporarily left Composition re-binds on return; a direct result with no matching registered slot is dropped. Intermediate pages do not receive a success because of it.

### Non-Compose business code

Plain callbacks keep the closure captured at request time, which suits non-Compose business code:

```kotlin
fun openEditor(
    navigator: Navigator,
    onResult: (RouteResult<UserResult>) -> Unit,
) {
    navigator.pushForResult(EditUserRoute(), onResult = onResult)
}
```

Such callbacks do not swap closures on recomposition or recreation; the caller must ensure the captured objects remain valid when the result is dispatched. `@DisallowComposableCalls` cannot prevent capturing external Compose state, so it is not a substitute for the Compose UI convention above.

## Interceptors

### Push: rewrite parameters or the target

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

Pages bind them via `@Router(pushInterceptors = [...])`; global interceptors are declared with `@RouterGlobalPushInterceptor`, owner-bound ones with `@RouterOwnerPushInterceptor`. Pop has corresponding annotations. Lower priority values run first; within one operation, global interceptors run before page interceptors.

When a Push interceptor rewrites the target type, the applicable interceptor chain restarts. Call `chain.interrupt()` to abort the navigation.

### Pop: inspect bound pages

```kotlin
import io.github.limuyang2.router.IRouterPopInterceptor
import io.github.limuyang2.router.PopResponse

class AuditPopInterceptor : IRouterPopInterceptor {
    override suspend fun intercept(
        chain: IRouterPopInterceptor.Chain,
    ): PopResponse {
        val request = chain.request()
        // Inspect the pages that will actually be closed via request.removeTargets.
        return chain.proceed()
    }
}
```

| PopRequest property | Meaning |
| --- | --- |
| `operation` | `CURRENT`, `REMOVE`, or `BACK_TO`. |
| `removeTargets` | The bound instances pending removal, ordered top to bottom at resolution time. |
| `backToPage` | The target page kept by `BACK_TO`; null for other operations. |
| `hasResult` | Whether a business value was explicitly provided; an explicit null also counts as true. |
| `result` | The raw business value; combine with `hasResult` to interpret null. |

Use `redirect` to change the page selection:

```kotlin
return chain.redirect {
    to<HomeRoute>()
}
```

To modify only the return value while keeping the original bound targets:

```kotlin
return chain.proceed(chain.request().withResult(newValue))
```

`CURRENT` supports rewriting the result; `BACK_TO` requires the original request to carry a result key and type; `REMOVE` does not support business results. Each Pop interceptor stage may call `proceed` or `redirect` at most once, and may call `chain.interrupt()` to abort. Changing the selection criteria re-resolves targets; identical criteria keep the originally bound instances.

See the [runtime API docs](lib_router/src/commonMain/kotlin/io/github/limuyang2/router) for interfaces and further constraints.

## Multi-module registration

KRouter completes cross-platform registration with explicit module indices and a host aggregate entry point.

| Module role | Plugin | Responsibility |
| --- | --- | --- |
| Feature module | `io.github.limuyang2.router.module` | Produces indices for this module's routes and interceptors. |
| Host module | `io.github.limuyang2.router` | Includes feature-module capabilities; aggregates selected dependencies' indices and generates the registration entry point. |

Configure the KSP processor for each production target / compilation. The host generates `RouterAggregatedRegistry.registerAll()` and calls it explicitly at startup; every platform uses the same initialization order.

- `moduleId` identifies the logical module and stays identical across its targets and variants.
- The default ID comes from `project.group:project.name`; when the group is empty, the project name is used. Override it with `router.moduleId`.
- The host selects indices from actual dependencies without scanning unrelated subprojects; a module appearing through a diamond dependency registers only once.
- Generated static entry points must be visible at compile time; the host depends on feature modules explicitly, or they are exposed through an intermediate module's `api`.
- Module ID conflicts, corrupt indices, or protocol incompatibilities are reported at build time.
- Aggregate output is generated per platform and must not be added to `commonMain` or fed back into KSP inputs.

The in-repo example is `composeApp → product / account → shared help`. The product and account modules both expose the help module via `api`; the host aggregates four module entry points, and the help module registers only once.

See the [module aggregation architecture doc](docs/router-plugin-architecture.md) for implementation details.

## Running the demo

After cloning, use the Gradle Wrapper. JDK 21 is required; the Android demo needs its SDK, iOS requires macOS and Xcode, and browser tests require Chrome. Build versions follow the [version catalog](gradle/libs.versions.toml) and [wrapper configuration](gradle/wrapper/gradle-wrapper.properties).

| Platform | Entry | Run or build |
| --- | --- | --- |
| Android | `androidApp` | `./gradlew :androidApp:assembleDebug`, then install the APK. |
| Desktop | `desktopApp` | `./gradlew :desktopApp:run` |
| iOS | `iosApp` | Open `iosApp/iosApp.xcodeproj` in Xcode and run it. |
| Wasm | `webApp` | `./gradlew :webApp:wasmJsBrowserDevelopmentRun` |
| Kotlin/JS | `webApp` | `./gradlew :webApp:jsBrowserDevelopmentRun` |

The home "route verification console" organizes cases into five categories: navigation and parameters, interceptors, result delivery, the navigation stack, and cross-module. Each case shows its input, expected outcome, and actual feedback, and pages display the current navigation stack.

The unregistered-URL case opens the demo fallback page from the push failure callback, preserving the original URL; unregistered instances show fallback content through `NavDisplay`'s fallback. The clean-stack case offers "Rebuild home" to restore the other verification entries.

## FAQ

### Why must a decorator be installed for result delivery?

`rememberRouteResult` needs the current entry's result scope, provided by `rememberRouteResultNavEntryDecorator()`. Providing only `LocalNavigator` is not enough to create the receiving slot.

### Why doesn't an unregistered URL automatically open a 404 page?

An unmatched URL is rejected from the stack, leaving the navigation stack unchanged. The business side can define a fallback strategy in `routeFailureCallback`. The demo's 404 page is one host-defined approach.

### Can I treat NavDisplay's backStack as an ordinary list?

Please modify it through `Navigator`. Direct insertions and removals bypass instance uniqueness, asynchronous target invalidation, and result settlement rules.

### Do route URLs sync to the browser address bar?

Currently URLs drive in-app navigation only; they do not sync the browser address bar or take over browser history.

### What if RouterAggregatedRegistry cannot be found?

Confirm that the host enables aggregation, the target module is visible at compile time with KSP configured for the corresponding target, then build that target. KMP-generated entry points live in platform source sets and cannot be referenced from `commonMain`. After customizing the package name, adjust the imports as well.

## License

KRouter is released under the [MIT License](LICENSE).
