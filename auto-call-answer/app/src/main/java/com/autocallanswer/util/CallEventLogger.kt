package com.autocallanswer.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CallEventLogger {
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _events = MutableStateFlow("")
    val events: StateFlow<String> = _events.asStateFlow()

    fun log(message: String) {
        val line = "${formatter.format(Date())}  $message"
        val current = _events.value
        _events.value = if (current.isEmpty()) line else "$current\n$line"
    }
}
