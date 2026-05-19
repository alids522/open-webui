package com.adoetz.gpt.flash.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.adoetz.gpt.flash.MainActivity
import com.adoetz.gpt.flash.R

/**
 * VoiceSessionService — Foreground service that keeps the microphone alive
 * when the user is in a live conversation / voice mode.
 *
 * This is started by JavaScript via FlashNative.startVoiceSession() and stopped
 * via FlashNative.stopVoiceSession().
 *
 * The foreground notification satisfies Android's requirement for background
 * microphone access. Without this, Android 12+ will kill the audio capture.
 *
 * WebSocket/audio streaming continuity is managed by the Open WebUI frontend
 * running in the WebView — this service only holds the wakelock and notification.
 */
class VoiceSessionService : LifecycleService() {

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        fun getService(): VoiceSessionService = this@VoiceSessionService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> startSession(intent.getStringExtra(EXTRA_SESSION_ID))
            ACTION_STOP -> stopSession()
        }

        return START_NOT_STICKY
    }

    private fun startSession(sessionId: String?) {
        // Acquire partial wakelock to keep CPU alive for WebSocket/audio
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AdoetzGPTFlash::VoiceSession"
        ).also {
            it.acquire(30 * 60 * 1000L) // max 30 minutes
        }

        startForeground(NOTIFICATION_ID, buildNotification(sessionId))
    }

    private fun stopSession() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    // ── Notification ──────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active voice conversation session"
                setShowBadge(false)
                setSound(null, null)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(sessionId: String?): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val tapPendingIntent = PendingIntent.getActivity(this, 0, tapIntent, pendingFlags)

        val stopIntent = Intent(this, VoiceSessionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.voice_session_title))
            .setContentText(getString(R.string.voice_session_subtitle))
            .setSmallIcon(R.drawable.ic_mic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(tapPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.voice_session_stop),
                stopPendingIntent
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "voice_session_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.adoetz.gpt.flash.START_VOICE_SESSION"
        const val ACTION_STOP  = "com.adoetz.gpt.flash.STOP_VOICE_SESSION"
        const val EXTRA_SESSION_ID = "session_id"

        fun start(context: Context, sessionId: String?) {
            val intent = Intent(context, VoiceSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceSessionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
