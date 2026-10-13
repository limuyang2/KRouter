package io.github.limuyang2.router

fun initRouter(config: RouterSingleton.Config.Builder.() -> Unit) {
    val built = RouterSingleton.Config.Builder().apply(config).build()
    RouterSingleton.init(built)
}

fun registerModuleRouters(vararg providers: ModuleRouter) {
    RouterSingleton.registerProviders(providers.toList())
}

object RouterSingleton {
    private var isInit = false
    private var mConfig: Config? = null
    internal val providers = mutableListOf<ModuleRouter>()

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

    fun getConfig(): Config {
        return mConfig ?: throw NullPointerException("Router not init")
    }

    fun getConfigOrNull(): Config? = mConfig

    class Config private constructor(
        val defaultScheme: String,
        val isLog: Boolean,
    ) {
        class Builder {
            private var defaultScheme: String? = null
            private var isLog: Boolean = false

            fun defaultScheme(defaultScheme: String) = apply {
                this.defaultScheme = defaultScheme
            }

            fun isLog(isLog: Boolean) = apply {
                this.isLog = isLog
            }

            fun build(): Config {
                return Config(
                    defaultScheme = defaultScheme ?: "route",
                    isLog = isLog,
                )
            }
        }
    }
}
