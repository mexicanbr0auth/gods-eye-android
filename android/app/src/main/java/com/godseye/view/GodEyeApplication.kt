package com.godseye.view

import android.app.Application
import com.godseye.view.data.AppDataStore

class GodEyeApplication : Application() {
    lateinit var dataStore: AppDataStore
        private set
    override fun onCreate() {
        super.onCreate()
        dataStore = AppDataStore(this)
    }
}
