package com.godseye.view

import android.app.Application
import android.webkit.WebView

class GodEyeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Habilita debug do WebView em builds debug
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
