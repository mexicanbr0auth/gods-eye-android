package com.godseye.view

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import com.godseye.view.data.AppDataStore
import com.godseye.view.data.MapKeyProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class GodEyeApplication : Application() {
    lateinit var dataStore: AppDataStore
        private set

    override fun onCreate() {
        super.onCreate()
        dataStore = AppDataStore(this)
        // Patch Google Maps SDK para ler key do DataStore em runtime (sem rebuild)
        // O SDK lê com.google.android.geo.API_KEY do manifest APENAS no primeiro MapsInitializer —
        // se patcharmos ApplicationInfo antes do primeiro GoogleMap ser criado, o Maps usa a key salva
        try {
            val saved = runBlocking { dataStore.googleMapsKey.first() }
            if (MapKeyProvider.isValidGoogleKey(saved)) {
                val ai = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                ai.metaData.putString("com.google.android.geo.API_KEY", saved!!.trim())
                Log.d("GodEye/App", "Runtime patch manifest API_KEY <- DataStore ${saved.take(8)}…")
            } else {
                Log.d("GodEye/App", "No valid DataStore key, keeping manifest placeholder")
            }
        } catch (e: Exception) {
            Log.w("GodEye/App", "Failed to patch manifest key", e)
        }
    }
}
