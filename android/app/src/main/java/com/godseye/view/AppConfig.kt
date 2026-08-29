package com.godseye.view

import android.content.Context
import android.content.SharedPreferences

class AppConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("godseye_prefs", Context.MODE_PRIVATE)

    var googleMapsKey: String?
        get() = prefs.getString("GOOGLE_MAPS_API_KEY", null)?.takeIf { it.isNotBlank() }
        set(v) = prefs.edit().putString("GOOGLE_MAPS_API_KEY", v).apply()

    var cesiumIonToken: String?
        get() = prefs.getString("CESIUM_ION_TOKEN", null)?.takeIf { it.isNotBlank() }
        set(v) = prefs.edit().putString("CESIUM_ION_TOKEN", v).apply()

    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean("has_seen_onboarding", false)
        set(v) = prefs.edit().putBoolean("has_seen_onboarding", v).apply()

    fun toJsInjection(): String {
        // Gera JS que popula localStorage e global para o app web ler
        val gKey = googleMapsKey?.let { escapeJs(it) } ?: "null"
        val cKey = cesiumIonToken?.let { escapeJs(it) } ?: "null"
        return """
            (function(){
              try {
                if ($gKey !== null) {
                  localStorage.setItem('GOOGLE_MAPS_API_KEY', $gKey);
                  window.GOOGLE_MAPS_API_KEY = $gKey;
                  window.__ANDROID_GOOGLE_KEY__ = $gKey;
                }
                if ($cKey !== null) {
                  localStorage.setItem('CESIUM_ION_TOKEN', $cKey);
                  window.CESIUM_ION_TOKEN = $cKey;
                }
                // Compat: se vite baked key estiver vazia, usa do Android
                if (!window.__ANDROID_KEYS_INJECTED__) {
                  window.__ANDROID_KEYS_INJECTED__ = true;
                  console.log('[GodEye Android] keys injected: google=' + ($gKey !== null) + ' cesium=' + ($cKey !== null));
                }
              } catch(e){ console.warn('[GodEye Android] key inject failed', e); }
            })();
        """.trimIndent()
    }

    private fun escapeJs(s: String): String {
        // JSON stringify escapa corretamente
        return "\"${s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
    }
}
