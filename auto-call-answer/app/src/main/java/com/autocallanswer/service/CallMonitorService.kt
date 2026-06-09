package com.autocallanswer.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.autocallanswer.AutoCallApp
import com.autocallanswer.R
import com.autocallanswer.call.CallAnswerManager
import com.autocallanswer.conversation.ConversationManager
import com.autocallanswer.ui.MainActivity
import com.autocallanswer.util.CallEventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var lastNumber: String? = null

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(TelephonyManager::class.java)
        startForegroundWithNotification()
        registerCallStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterCallStateListener()
        ConversationManager.stopActive()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .build()
    }

    private fun registerCallStateListener() {
        val manager = telephonyManager ?: return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallStateChanged(state, null)
                }
            }
            telephonyCallback = callback
            manager.registerTelephonyCallback(mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallStateChanged(state, phoneNumber)
                }
            }
            phoneStateListener = listener
            @Suppress("DEPRECATION")
            manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun unregisterCallStateListener() {
        val manager = telephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { manager.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener?.let { manager.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
    }

    private fun handleCallStateChanged(state: Int, phoneNumber: String?) {
        val app = application as AutoCallApp
        if (!app.preferences.autoAnswerEnabled) return
        if (state == lastState && phoneNumber == lastNumber) return

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                lastNumber = phoneNumber
                CallEventLogger.log("Service detected ringing from ${phoneNumber ?: "unknown"}")
                scope.launch {
                    delay(800)
                    if (CallAnswerManager.isRinging(this@CallMonitorService)) {
                        CallAnswerManager.answerIncomingCall(this@CallMonitorService)
                    }
                }
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    CallEventLogger.log("Service detected connected call")
                    scope.launch {
                        delay(1200)
                        ConversationManager.startForCall(this@CallMonitorService, lastNumber)
                    }
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                ConversationManager.stopActive()
                lastNumber = null
            }
        }

        lastState = state
    }

    companion object {
        const val CHANNEL_ID = "call_monitor_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
