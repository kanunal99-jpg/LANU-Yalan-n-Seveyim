package com.lanu.yalan

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LanuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Fatal errors are handed back to Android. Never swallow a crash or log conversation text.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            getSharedPreferences("health", MODE_PRIVATE).edit().putBoolean("interrupted", true).commit()
            previous?.uncaughtException(thread, throwable)
        }
    }
}
