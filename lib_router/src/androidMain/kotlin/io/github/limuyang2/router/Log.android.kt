package io.github.limuyang2.router

internal actual object Log {
    actual fun i(tag: String, message: String) {
        android.util.Log.i(tag, message)
    }

    actual fun e(tag: String, message: String) {
        android.util.Log.e(tag, message)
    }
}
