package io.github.limuyang2.router

/**
 * 跨平台日志工具，内部使用，各平台实现分别对接 android.util.Log / NSLog / println。
 */
internal expect object Log {
    fun i(tag: String, message: String)

    fun e(tag: String, message: String)
}
