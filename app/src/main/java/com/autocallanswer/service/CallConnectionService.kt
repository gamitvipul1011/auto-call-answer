package com.autocallanswer.service

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.autocallanswer.util.CallEventLogger

class CallConnectionService : ConnectionService() {
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val connection = VoiceConnection()
        connection.setRinging()
        connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(
            request?.address?.schemeSpecificPart,
            TelecomManager.PRESENTATION_ALLOWED
        )
        CallEventLogger.log("ConnectionService created incoming connection")
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val connection = VoiceConnection()
        connection.setDialing()
        connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        return connection
    }

    private class VoiceConnection : Connection() {
        override fun onAnswer() {
            super.onAnswer()
            setActive()
            CallEventLogger.log("Connection answered")
        }

        override fun onReject() {
            super.onReject()
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            destroy()
        }

        override fun onDisconnect() {
            super.onDisconnect()
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        }

        override fun onCallAudioStateChanged(state: android.telecom.CallAudioState?) {
            super.onCallAudioStateChanged(state)
        }
    }
}
