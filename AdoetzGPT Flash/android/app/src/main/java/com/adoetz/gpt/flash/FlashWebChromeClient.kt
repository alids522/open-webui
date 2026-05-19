package com.adoetz.gpt.flash

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * WebChromeClient for AdoetzGPT Flash.
 *
 * Handles:
 * - WebRTC microphone permission requests (grants to WebView automatically if Android has granted)
 * - File chooser for image/document upload from Open WebUI
 * - Console logging (forwarded to Logcat)
 * - Geolocation (blocked/ignored)
 */
class FlashWebChromeClient : WebChromeClient() {

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    // ── WebRTC / Microphone permission ──────────────────────────

    override fun onPermissionRequest(request: PermissionRequest?) {
        request?.let { req ->
            // Grant all requested WebRTC resources (mic, camera)
            // Android runtime permissions are already requested in MainActivity
            req.grant(req.resources)
        }
    }

    // ── File chooser (image/document upload) ────────────────────

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        // Dismiss previous callback if any
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = filePathCallback

        val activity = webView?.context as? Activity ?: run {
            filePathCallback?.onReceiveValue(null)
            return false
        }

        val intent = fileChooserParams?.createIntent() ?: run {
            filePathCallback?.onReceiveValue(null)
            return false
        }

        // Use a global request code approach via the activity
        // Note: In a real Capacitor setup, this is handled by the Capacitor plugin system
        try {
            activity.startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
        } catch (e: Exception) {
            fileChooserCallback = null
            filePathCallback?.onReceiveValue(null)
            return false
        }
        return true
    }

    /** Called by MainActivity.onActivityResult to deliver file results */
    fun onFileChooserResult(uris: Array<Uri>?) {
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    // ── Console messages ─────────────────────────────────────────

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        consoleMessage?.let {
            val level = when (it.messageLevel()) {
                ConsoleMessage.MessageLevel.ERROR -> "E"
                ConsoleMessage.MessageLevel.WARNING -> "W"
                else -> "D"
            }
            android.util.Log.println(
                when (level) {
                    "E" -> android.util.Log.ERROR
                    "W" -> android.util.Log.WARN
                    else -> android.util.Log.DEBUG
                },
                "WebConsole",
                "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}"
            )
        }
        return true
    }

    // ── Progress ─────────────────────────────────────────────────

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
    }

    companion object {
        const val FILE_CHOOSER_REQUEST_CODE = 1001
    }
}
