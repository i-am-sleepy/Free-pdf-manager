package com.freepdf.manager

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Free PDF Manager application class.
 * Hilt entry point; also the place for global singletons later
 * (e.g. OpenCV loader, PDFBox init) — kept intentionally minimal in v1.
 */
@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
