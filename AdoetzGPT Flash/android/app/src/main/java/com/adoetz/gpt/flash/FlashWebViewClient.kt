package com.adoetz.gpt.flash

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*

/**
 * Custom WebViewClient for AdoetzGPT Flash.
 *
 * Key behaviors:
 * - Allows navigation to the backend URL (remote Open WebUI server).
 * - Opens external links in the system browser, not inside the WebView.
 * - Handles SSL errors gracefully.
 * - Passes navigation control to the activity for state tracking.
 */
class FlashWebViewClient(private val activity: MainActivity) : WebViewClient() {

    // Domains that should stay inside the WebView
    private var backendHost: String? = null

    fun setBackendHost(host: String?) {
        backendHost = host
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url ?: return false
        val scheme = url.scheme ?: return false

        // Only handle http/https
        if (scheme != "http" && scheme != "https") return false

        val host = url.host ?: return false
        val currentHost = backendHost ?: view?.url?.let {
            runCatching { java.net.URL(it).host }.getOrNull()
        }

        // Stay inside WebView if navigating within the backend
        if (currentHost != null && host == currentHost) return false

        // For the bundled setup page (file:// → external URL), allow navigation
        val currentUrl = view?.url ?: ""
        if (currentUrl.startsWith("file://")) {
            // This is the setup → backend redirect: allow it
            setBackendHost(host)
            return false
        }

        // External link → open in system browser
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url.toString())
            )
            activity.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.w("FlashWebView", "Could not open external URL: $url")
        }
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        android.util.Log.d("FlashWebView", "Loading: $url")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        android.util.Log.d("FlashWebView", "Loaded: $url")

        // Track backend host for future navigation decisions
        if (url != null && !url.startsWith("file://")) {
            try {
                val host = java.net.URL(url).host
                setBackendHost(host)
            } catch (_: Exception) {}
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            android.util.Log.e(
                "FlashWebView",
                "Error loading ${request.url}: ${error?.description} (${error?.errorCode})"
            )
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        // For development/self-signed certs: allow. 
        // In production builds, you may want to call handler?.cancel() instead.
        android.util.Log.w("FlashWebView", "SSL error: ${error?.primaryError} — proceeding anyway (dev)")
        handler?.proceed()
    }
}
