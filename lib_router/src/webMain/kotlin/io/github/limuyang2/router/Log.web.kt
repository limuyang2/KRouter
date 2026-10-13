package io.github.limuyang2.router

internal actual object Log {
    actual fun i(tag: String, message: String) {
        println("$tag $message")
    }

    actual fun e(tag: String, message: String) {
        println("$tag $message")
    }
}
