package io.github.limuyang2.router.demo

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        initDemoRouter()
    }
}
