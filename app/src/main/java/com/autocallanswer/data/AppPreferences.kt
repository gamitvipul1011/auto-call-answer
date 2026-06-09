package com.autocallanswer.data

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoAnswerEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ANSWER, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ANSWER, value).apply()

    var openAiApiKey: String
        get() = prefs.getString(KEY_OPENAI_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_OPENAI_API_KEY, value.trim()).apply()

    var greetingMessage: String
        get() = prefs.getString(KEY_GREETING, DEFAULT_GREETING).orEmpty()
        set(value) = prefs.edit().putString(KEY_GREETING, value.trim()).apply()

    companion object {
        private const val PREFS_NAME = "auto_call_answer_prefs"
        private const val KEY_AUTO_ANSWER = "auto_answer_enabled"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_GREETING = "greeting_message"

        const val DEFAULT_GREETING =
            "Hello, the person you are calling is unavailable right now. " +
                "I am their assistant. Please tell me who is calling and what this is regarding."
    }
}
