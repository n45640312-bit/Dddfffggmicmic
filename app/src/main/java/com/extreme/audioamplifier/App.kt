package com.extreme.audioamplifier

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
