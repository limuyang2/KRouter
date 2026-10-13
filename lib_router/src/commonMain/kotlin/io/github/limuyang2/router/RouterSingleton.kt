package io.github.limuyang2.router

/**
 * 初始化全局路由配置；重复调用不替换首次配置。应在模块注册和创建 Navigator 前调用。
 *
 * Initializes global routing configuration; repeated calls keep the first configuration.
 * Call before registering modules and creating a Navigator.
 *
 * @param config 配置 DSL / Configuration DSL.
 */
fun initRouter(config: RouterSingleton.Config.Builder.() -> Unit) {
    val built = RouterSingleton.Config.Builder().apply(config).build()
    RouterSingleton.init(built)
}

/**
 * 注册显式模块 provider，按 provider 类型去重；运行时已初始化的 Navigator 不会重新加载拦截器列表。
 *
 * Registers explicit module providers, deduplicated by provider class. Navigators with initialized
 * runtimes do not reload their interceptor lists; register modules before initializing those runtimes.
 *
 * @param providers 模块注册表，通常由生成的聚合入口提供 / Module registries, usually supplied by the generated aggregate.
 * @throws IllegalStateException 非空注册发生在初始化前 / Nonempty registration before initialization.
 */
fun registerModuleRouters(vararg providers: ModuleRouter) {
    RouterSingleton.registerProviders(providers.toList())
}

/**
 * 全局路由配置和显式模块注册入口
 *
 * Global routing configuration and explicit module registration.
 */
object RouterSingleton {
    private var isInit = false
    private var mConfig: Config? = null
    internal val providers = mutableListOf<ModuleRouter>()

    /**
     * 应用首次配置并初始化模块列表；已初始化时不执行。
     *
     * Applies the first configuration and initializes module storage; does nothing after initialization.
     *
     * @param config 全局配置 / Global configuration.
     */
    fun init(config: Config) {
        if (isInit) return

        mConfig = config
        providers.clear()
        isInit = true
    }

    internal fun registerProviders(newProviders: List<ModuleRouter>) {
        if (newProviders.isEmpty()) return
        check(isInit) { "Router not init, please call initRouter {} before registerModuleRouters(...)" }

        val existingKeys = providers
            .asSequence()
            .map { it::class }
            .toMutableSet()
        newProviders.forEach { provider ->
            val key = provider::class
            if (existingKeys.add(key)) {
                providers.add(provider)
            }
        }
    }

    /**
     * 返回已初始化的配置。
     *
     * Returns the initialized configuration.
     *
     * @throws NullPointerException 尚未初始化 / Routing has not been initialized.
     */
    fun getConfig(): Config {
        return mConfig ?: throw NullPointerException("Router not init")
    }

    /**
     * 返回当前配置，未初始化时为 null
     *
     * Returns the configuration, or null before initialization.
     */
    fun getConfigOrNull(): Config? = mConfig

    /**
     * 不可变的全局路由配置
     *
     * Immutable global routing configuration.
     */
    class Config private constructor(
        val defaultScheme: String,
        val isLog: Boolean,
    ) {
        /**
         * 全局配置构建器
         *
         * Global configuration builder.
         */
        class Builder {
            private var defaultScheme: String? = null
            private var isLog: Boolean = false

            /**
             * 设置未显式声明 scheme 的路由所用协议，默认值为 route。
             *
             * Sets the scheme for routes without an explicit scheme; defaults to route.
             *
             * @param defaultScheme 默认路由协议 / Default route scheme.
             */
            fun defaultScheme(defaultScheme: String) = apply {
                this.defaultScheme = defaultScheme
            }

            /**
             * 开启或关闭路由日志，默认关闭
             *
             * Enables or disables routing logs; disabled by default.
             */
            fun isLog(isLog: Boolean) = apply {
                this.isLog = isLog
            }

            /**
             * 构建配置，不执行初始化
             *
             * Builds the configuration without initializing routing.
             */
            fun build(): Config {
                return Config(
                    defaultScheme = defaultScheme ?: "route",
                    isLog = isLog,
                )
            }
        }
    }
}
