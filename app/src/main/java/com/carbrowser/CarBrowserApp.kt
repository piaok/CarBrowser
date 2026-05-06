package com.carbrowser

import android.app.Application

class CarBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 预加载广告拦截规则
        AdBlocker.init(this)
    }
}
