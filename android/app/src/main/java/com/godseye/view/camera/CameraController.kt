package com.godseye.view.camera

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

// Porta nativa de src/camera.js + src/orbit.js + src/cockpitTracking.js + src/cameraVerbs.js
// No web, Cesium Viewer controla globe 3D; no nativo, GoogleMap CameraPosition + tilt/bearing/zoom

data class GodEyeCamera(
    val target: LatLng,
    val zoom: Float = 2f,      // 2 = globe, 19 = rua (igual web z)
    val tilt: Float = 0f,      // 0 = nadir, 67 = cockpit
    val bearing: Float = 0f
)

class CameraController {
    var current = GodEyeCamera(LatLng(0.0,0.0))
        private set

    fun globe(): CameraPosition = CameraPosition.builder()
        .target(LatLng(20.0,0.0)).zoom(2f).tilt(0f).bearing(0f).build() // Reset Globe — src/ui.js #reset-globe-view

    fun flyTo(latLng: LatLng, zoom: Float = 12f, tilt: Float = 45f): CameraPosition =
        CameraPosition.builder().target(latLng).zoom(zoom).tilt(tilt).bearing(0f).build()

    fun cockpit(flightPos: LatLng, heading: Float): CameraPosition =
        // src/cockpitTracking.js — câmera trava no avião, tilt alto
        CameraPosition.builder().target(flightPos).zoom(16f).tilt(67f).bearing(heading).build()

    fun orbit(center: LatLng, radiusZoom: Float = 10f): CameraPosition =
        CameraPosition.builder().target(center).zoom(radiusZoom).tilt(30f).bearing(0f).build()
}
