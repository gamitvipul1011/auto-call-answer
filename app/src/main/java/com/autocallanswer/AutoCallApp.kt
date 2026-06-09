package com.autocallanswer

import android.app.Application
import com.autocallanswer.data.AppPreferences

class AutoCallApp : Application() {
    lateinit var preferences: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
    }
}
