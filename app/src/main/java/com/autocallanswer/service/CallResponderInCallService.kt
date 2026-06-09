package com.autocallanswer.service

import android.telecom.Call
import android.telecom.InCallService
import com.autocallanswer.AutoCallApp
import com.autocallanswer.conversation.ConversationManager
import com.autocallanswer.util.CallEventLogger

class CallResponderInCallService : InCallService() {
    private val activeCallbacks = mutableMapOf<Call, Call.Callback>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val app = application as AutoCallApp
        if (!app.preferences.autoAnswerEnabled) return

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                when (state) {
                    Call.STATE_ACTIVE -> {
                        val number = call.details.handle?.schemeSpecificPart
                        CallEventLogger.log("InCallService active: $number")
                        ConversationManager.startForCall(this@CallResponderInCallService, number)
                    }

                    Call.STATE_DISCONNECTED -> {
                        ConversationManager.stopActive()
                        call.unregisterCallback(this)
                        activeCallbacks.remove(call)
                    }
                }
            }
        }

        call.registerCallback(callback)
        activeCallbacks[call] = callback

        if (call.state == Call.STATE_RINGING) {
            CallEventLogger.log("InCallService answering ringing call")
            call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
        }
    }

    override fun onCallRemoved(call: Call) {
        activeCallbacks.remove(call)?.let { call.unregisterCallback(it) }
        ConversationManager.stopActive()
        super.onCallRemoved(call)
    }
}
