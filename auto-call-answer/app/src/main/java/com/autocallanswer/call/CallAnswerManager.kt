package com.autocallanswer.call

import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.autocallanswer.util.CallEventLogger

object CallAnswerManager {
    fun answerIncomingCall(context: Context): Boolean {
        val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return false

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ANSWER_PHONE_CALLS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            CallEventLogger.log("Cannot answer: missing ANSWER_PHONE_CALLS permission")
            return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecomManager.acceptRingingCall()
            } else {
                @Suppress("DEPRECATION")
                telecomManager.acceptRingingCall()
            }
            CallEventLogger.log("Answered incoming call")
            true
        } catch (securityException: SecurityException) {
            CallEventLogger.log("Failed to answer call: ${securityException.message}")
            false
        } catch (illegalStateException: IllegalStateException) {
            CallEventLogger.log("No ringing call to answer")
            false
        }
    }

    fun isRinging(context: Context): Boolean {
        val telephonyManager = context.getSystemService(TelephonyManager::class.java) ?: return false
        return telephonyManager.callState == TelephonyManager.CALL_STATE_RINGING
    }
}
