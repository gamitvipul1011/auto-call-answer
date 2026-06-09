package com.autocallanswer.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.autocallanswer.util.CallEventLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class CallerSpeechListener(context: Context) {
    private val appContext = context.applicationContext

    suspend fun listenOnce(timeoutMs: Long = 12_000L): String? {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            CallEventLogger.log("Speech recognition unavailable on this device")
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            }

            val timeoutRunnable = Runnable {
                if (continuation.isActive) {
                    recognizer.cancel()
                    recognizer.destroy()
                    continuation.resume(null)
                }
            }
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed(timeoutRunnable, timeoutMs)

            continuation.invokeOnCancellation {
                handler.removeCallbacks(timeoutRunnable)
                recognizer.cancel()
                recognizer.destroy()
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) {
                    handler.removeCallbacks(timeoutRunnable)
                    recognizer.destroy()
                    CallEventLogger.log("Speech recognition error: $error")
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onResults(results: Bundle?) {
                    handler.removeCallbacks(timeoutRunnable)
                    recognizer.destroy()
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (continuation.isActive) continuation.resume(text?.takeIf { it.isNotEmpty() })
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
            })

            recognizer.startListening(intent)
        }
    }
}
