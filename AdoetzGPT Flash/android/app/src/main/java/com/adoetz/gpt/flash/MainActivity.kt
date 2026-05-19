package com.adoetz.gpt.flash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.adoetz.gpt.flash.service.VoiceSessionService
import com.adoetz.gpt.flash.utils.BackendPreferences
import com.adoetz.gpt.flash.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * MainActivity — hosts the Capacitor WebView.
 *
 * Flow:
 *   1. On first launch: shows backend setup screen (index.html handles this via localStorage).
 *   2. After setup: WebView redirects to the Open WebUI backend URL.
 *   3. On subsequent launches: directly loads the backend URL.
 *
 * The setup screen runs inside the bundled assets (frontend/build/index.html).
 * After the user enters and confirms a URL, JavaScript calls window.location.href
 * which the WebViewClient intercepts — if it's an external URL, we navigate there.
 * The WebView then loads the full Open WebUI frontend from the remote server.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: BackendPreferences
    private var webView: WebView? = null
    private var isWebViewLoaded = false

    // Permission launcher
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val audioGranted = results[Manifest.permission.RECORD_AUDIO] ?: false
            if (!audioGranted) {
                Toast.makeText(this, getString(R.string.mic_permission_required), Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display with proper insets handling
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = BackendPreferences(this)

        requestPermissions()
        setupWebView(savedInstanceState)
        bindVoiceService()
    }

    // ─────────────────────────────────────────
    // Permissions
    // ─────────────────────────────────────────

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.RECORD_AUDIO))
            needed += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS))
                needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun hasPermission(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    // ─────────────────────────────────────────
    // WebView Setup
    // ─────────────────────────────────────────

    private fun setupWebView(savedInstanceState: Bundle?) {
        webView = binding.webView

        webView?.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                textZoom = 100
                allowFileAccess = true
                allowContentAccess = true
                setMediaPlaybackRequiresUserGesture(false)
                // User agent to identify as Android app
                userAgentString = "$userAgentString AdoetzGPTFlash/1.0 Android"
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@apply, true)
            }

            webViewClient = FlashWebViewClient(this@MainActivity)
            webChromeClient = FlashWebChromeClient()

            // Bridge JavaScript interface for native calls
            addJavascriptInterface(
                NativeBridge(this@MainActivity),
                "FlashNative"
            )

            // Restore or load initial URL
            if (savedInstanceState != null) {
                val wvState = savedInstanceState.getBundle(KEY_WEBVIEW_STATE)
                if (wvState != null) {
                    restoreState(wvState)
                    isWebViewLoaded = true
                    return
                }
            }

            // Load the bundled setup/loader page
            loadUrl(BUNDLED_URL)
        }
    }

    // ─────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        webView?.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
        webView?.pauseTimers()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView?.let { wv ->
            val wvBundle = Bundle()
            wv.saveState(wvBundle)
            outState.putBundle(KEY_WEBVIEW_STATE, wvBundle)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val wvState = savedInstanceState.getBundle(KEY_WEBVIEW_STATE)
        if (wvState != null && webView != null) {
            webView!!.restoreState(wvState)
            isWebViewLoaded = true
        }
    }

    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.destroy()
        webView = null
        unbindVoiceService()
    }

    // ─────────────────────────────────────────
    // VoiceSession Service Binding
    // ─────────────────────────────────────────

    private var voiceServiceBound = false
    private val voiceServiceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
            voiceServiceBound = true
        }
        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            voiceServiceBound = false
        }
    }

    private fun bindVoiceService() {
        val intent = Intent(this, VoiceSessionService::class.java)
        bindService(intent, voiceServiceConnection, BIND_AUTO_CREATE)
    }

    private fun unbindVoiceService() {
        if (voiceServiceBound) {
            unbindService(voiceServiceConnection)
            voiceServiceBound = false
        }
    }

    // ─────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────

    /** Called from NativeBridge when setup completes and user saves a URL */
    fun onBackendUrlSaved(url: String) {
        lifecycleScope.launch {
            prefs.saveBackendUrl(url)
        }
    }

    /** Called from NativeBridge to clear saved URL (logout/reset) */
    fun onClearBackendUrl() {
        lifecycleScope.launch {
            prefs.clearBackendUrl()
        }
    }

    companion object {
        private const val KEY_WEBVIEW_STATE = "webview_state"
        // Capacitor serves bundled assets from this URL in release.
        // In CI, the frontend/build is copied to android/app/src/main/assets/public.
        const val BUNDLED_URL = "file:///android_asset/public/index.html"
    }
}
