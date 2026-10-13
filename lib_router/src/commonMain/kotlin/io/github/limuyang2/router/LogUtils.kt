package io.github.limuyang2.router

internal inline fun logI(msg: () -> String) {
    if (RouterSingleton.getConfigOrNull()?.isLog == true) {
        Log.i("Router ==>", msg())
    }
}

internal inline fun logE(msg: () -> String) {
    if (RouterSingleton.getConfigOrNull()?.isLog == true) {
        Log.e("Router ==>", msg())
    }
}
