package com.godseye.view.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefs by preferencesDataStore("godseye")

class AppDataStore(private val ctx: Context) {
    companion object {
        val KEY_GOOGLE = stringPreferencesKey("GOOGLE_MAPS_API_KEY")
        val KEY_CESIUM = stringPreferencesKey("CESIUM_ION_TOKEN")
        val KEY_OPENAI = stringPreferencesKey("OPENAI_API_KEY")
        val KEY_AIS = stringPreferencesKey("AISSTREAM_API_KEY")
        val KEY_FIRMS = stringPreferencesKey("FIRMS_MAP_KEY")
        val KEY_TOMTOM = stringPreferencesKey("TOMTOM_API_KEY")
        val KEY_ONBOARD = booleanPreferencesKey("onboard_done")
    }
    val googleMapsKey: Flow<String?> = ctx.prefs.data.map { it[KEY_GOOGLE]?.takeIf { v -> v.isNotBlank() } }
    val cesiumToken: Flow<String?> = ctx.prefs.data.map { it[KEY_CESIUM]?.takeIf { v -> v.isNotBlank() } }
    val openAIKey: Flow<String?> = ctx.prefs.data.map { it[KEY_OPENAI]?.takeIf { v -> v.isNotBlank() } }
    val aisKey: Flow<String?> = ctx.prefs.data.map { it[KEY_AIS]?.takeIf { v -> v.isNotBlank() } }
    suspend fun setGoogleKey(v: String) { ctx.prefs.edit { it[KEY_GOOGLE] = v.trim() } }
    suspend fun clearGoogleKey() { ctx.prefs.edit { it.remove(KEY_GOOGLE) } }
    suspend fun setCesium(v: String) { ctx.prefs.edit { it[KEY_CESIUM] = v.trim() } }
    suspend fun setOpenAI(v: String) { ctx.prefs.edit { it[KEY_OPENAI] = v.trim() } }
    suspend fun setOnboard(v: Boolean) { ctx.prefs.edit { it[KEY_ONBOARD] = v } }
    val onboardDone: Flow<Boolean> = ctx.prefs.data.map { it[KEY_ONBOARD] ?: false }

    // Debug: leitura síncrona para logs
    suspend fun debugDump(): Map<String,String?> {
        val p = ctx.prefs.data.map { it }.let {
            var out: Map<String,String?> = emptyMap()
            kotlinx.coroutines.runBlocking { it.collect { pref -> out = mapOf(
                "google" to pref[KEY_GOOGLE],
                "cesium" to pref[KEY_CESIUM],
                "openai" to pref[KEY_OPENAI]
            ) } }
            out
        }
        return p
    }
}
