package com.godseye.view.data

import android.content.Context
import android.content.pm.PackageManager

object MapKeyProvider {
    fun getManifestKey(ctx: Context): String? {
        return try {
            val ai = ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA)
            ai.metaData?.getString("com.google.android.geo.API_KEY")?.takeIf { it.isNotBlank() && it != "YOUR_GOOGLE_MAPS_API_KEY" }
        } catch (_: Exception) { null }
    }
    // Validação relaxada — antes exigia AIza e length 20, bloqueava salvar
    fun isValidGoogleKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val t = key.trim()
        if (t == "YOUR_GOOGLE_MAPS_API_KEY") return false
        if (t.length < 10) return false
        return true // aceita qualquer key não vazia; Google valida no servidor
    }
}
