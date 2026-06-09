package com.autocallanswer.conversation

import android.content.Context
import com.autocallanswer.ai.OpenAiClient
import com.autocallanswer.data.AppPreferences
import com.autocallanswer.speech.CallSpeaker
import com.autocallanswer.speech.CallerSpeechListener
import com.autocallanswer.util.CallEventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ConversationManager(
    context: Context,
    private val preferences: AppPreferences
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val speaker = CallSpeaker(appContext)
    private val listener = CallerSpeechListener(appContext)
    private val history = mutableListOf<Pair<String, String>>()
    private var running = false

    fun start(callerNumber: String?) {
        if (running) return
        running = true
        history.clear()

        scope.launch {
            try {
                val greeting = preferences.greetingMessage.ifBlank { AppPreferences.DEFAULT_GREETING }
                CallEventLogger.log("Speaking greeting to caller")
                speaker.speak(greeting)
                history += "assistant" to greeting

                repeat(MAX_TURNS) { turn ->
                    CallEventLogger.log("Listening for caller (turn ${turn + 1})")
                    val callerText = listener.listenOnce()
                    if (callerText.isNullOrBlank()) {
                        CallEventLogger.log("No caller speech detected")
                        speaker.speak("I did not catch that. Thank you for calling. Goodbye.")
                        return@launch
                    }

                    CallEventLogger.log("Caller said: $callerText")
                    history += "user" to callerText

                    val reply = buildReply(callerNumber, callerText)
                    CallEventLogger.log("Assistant reply: $reply")
                    history += "assistant" to reply
                    speaker.speak(reply)

                    if (shouldEndConversation(reply)) {
                        return@launch
                    }
                }

                speaker.speak("Thank you for calling. I will pass this message along. Goodbye.")
            } catch (error: Exception) {
                CallEventLogger.log("Conversation error: ${error.message}")
            } finally {
                shutdown()
            }
        }
    }

    private suspend fun buildReply(callerNumber: String?, callerText: String): String {
        val apiKey = preferences.openAiApiKey
        if (apiKey.isBlank()) {
            return "Thanks for your message. The owner is unavailable right now, but I have noted your call."
        }

        val systemPrompt = """
            You are a polite phone assistant answering calls on behalf of the device owner.
            Keep responses short (1-2 sentences), natural for spoken conversation, and professional.
            The caller number is ${callerNumber ?: "unknown"}.
            If the caller seems finished, end with a brief goodbye.
        """.trimIndent()

        return try {
            OpenAiClient(apiKey).generateReply(systemPrompt, history)
        } catch (error: Exception) {
            CallEventLogger.log("AI reply failed: ${error.message}")
            "Thanks, I have noted your message and will let them know you called."
        }
    }

    private fun shouldEndConversation(reply: String): Boolean {
        val normalized = reply.lowercase()
        return normalized.contains("goodbye") || normalized.contains("good bye")
    }

    fun shutdown() {
        running = false
        speaker.shutdown()
        scope.cancel()
    }

    companion object {
        private const val MAX_TURNS = 4

        @Volatile
        private var active: ConversationManager? = null

        fun startForCall(context: Context, callerNumber: String?) {
            val app = context.applicationContext as com.autocallanswer.AutoCallApp
            synchronized(this) {
                active?.shutdown()
                active = ConversationManager(context, app.preferences).also {
                    it.start(callerNumber)
                }
            }
        }

        fun stopActive() {
            synchronized(this) {
                active?.shutdown()
                active = null
            }
        }
    }
}
