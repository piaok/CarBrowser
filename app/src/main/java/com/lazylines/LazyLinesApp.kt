package com.lazylines

import android.app.Application
import com.lazylines.adblock.AdBlocker

class LazyLinesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdBlocker.init(this)
    }
}
