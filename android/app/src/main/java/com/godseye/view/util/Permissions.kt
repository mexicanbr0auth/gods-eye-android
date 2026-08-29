package com.godseye.view.util

import android.Manifest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus

// Centraliza permissões — equivale a vite middlewares rateLimiter + manifest perms
// No web: GEV_RATELIMIT_*; no nativo: runtime permission + OkHttp throttling

@OptIn(ExperimentalPermissionsApi::class)
fun isGranted(s: PermissionStatus) = s is PermissionStatus.Granted

object RequiredPermissions {
    val all = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.RECORD_AUDIO,
    )
}
