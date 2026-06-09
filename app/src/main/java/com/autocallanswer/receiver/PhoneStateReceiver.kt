package com.autocallanswer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.autocallanswer.AutoCallApp
import com.autocallanswer.call.CallAnswerManager
import com.autocallanswer.conversation.ConversationManager
import com.autocallanswer.util.CallEventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PhoneStateReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val app = context.applicationContext as? AutoCallApp ?: return
        if (!app.preferences.autoAnswerEnabled) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                CallEventLogger.log("Incoming call from ${number ?: "unknown"}")
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        delay(800)
                        if (CallAnswerManager.isRinging(context)) {
                            CallAnswerManager.answerIncomingCall(context)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                CallEventLogger.log("Call connected")
                scope.launch {
                    delay(1200)
                    ConversationManager.startForCall(context, number)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                CallEventLogger.log("Call ended")
                ConversationManager.stopActive()
            }
        }
    }
}
