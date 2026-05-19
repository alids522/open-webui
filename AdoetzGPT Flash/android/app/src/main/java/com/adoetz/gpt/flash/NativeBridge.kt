package com.adoetz.gpt.flash

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import com.adoetz.gpt.flash.service.VoiceSessionService

/**
 * NativeBridge — JavaScript Interface exposed as `window.FlashNative`
 *
 * Called from the bundled frontend (setup page + any injected scripts).
 *
 * Methods callable from JavaScript:
 *   FlashNative.saveBackendUrl(url)   — persist backend URL natively
 *   FlashNative.clearBackendUrl()     — clear saved URL (logout/reset)
 *   FlashNative.getSavedBackendUrl()  — read saved URL (sync)
 *   FlashNative.startVoiceSession()   — start foreground mic service
 *   FlashNative.stopVoiceSession()    — stop foreground mic service
 *   FlashNative.showToast(msg)        — show Android Toast
 *   FlashNative.getAppVersion()       — return BuildConfig.VERSION_NAME
 *   FlashNative.isAndroid()           — returns true (useful for JS feature detection)
 */
class NativeBridge(private val activity: MainActivity) {

    private val context: Context get() = activity.applicationContext

    @JavascriptInterface
    fun saveBackendUrl(url: String) {
        activity.onBackendUrlSaved(url)
    }

    @JavascriptInterface
    fun clearBackendUrl() {
        activity.onClearBackendUrl()
    }

    @JavascriptInterface
    fun getSavedBackendUrl(): String {
        // Synchronous read from shared preferences
        return android.preference.PreferenceManager
            .getDefaultSharedPreferences(context)
            .getString("backend_url", "") ?: ""
    }

    @JavascriptInterface
    fun startVoiceSession(sessionId: String?) {
        VoiceSessionService.start(context, sessionId)
    }

    @JavascriptInterface
    fun stopVoiceSession() {
        VoiceSessionService.stop(context)
    }

    @JavascriptInterface
    fun showToast(message: String) {
        activity.runOnUiThread {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun getAppVersion(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun isAndroid(): Boolean = true

    @JavascriptInterface
    fun openSettings() {
        activity.runOnUiThread {
            // Navigate WebView back to bundled setup page
            activity.window.decorView.findViewWithTag<android.webkit.WebView>("flash_webview")
                ?.loadUrl(MainActivity.BUNDLED_URL)
        }
    }
}
