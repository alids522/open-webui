package com.adoetz.gpt.flash

import android.app.Application
import android.webkit.WebView
import android.os.Build

class FlashApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Enable WebView debugging in debug builds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        }
    }
}
