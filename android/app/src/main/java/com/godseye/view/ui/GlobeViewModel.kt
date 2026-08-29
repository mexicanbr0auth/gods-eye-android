package com.godseye.view.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godseye.view.camera.CameraController
import com.godseye.view.camera.GodEyeCamera
import com.godseye.view.data.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.*

class GlobeViewModel : ViewModel() {
    // Equivale a src/ui.js StyleManager + src/data/manager.js + src/hud.js
    val cameraController = CameraController()
    val dataManager = DataLayerManager()

    // Layers — espelha src/data/layerState.js LAYER_STATE_REGISTRY
    private val _layers = MutableStateFlow(setOf(LayerId.FLIGHTS, LayerId.EARTHQUAKES))
    val layers: StateFlow<Set<LayerId>> = _layers.asStateFlow()

    val flights = dataManager.flights.flights
    val quakes = dataManager.quakes.quakes
    val sats = dataManager.sats.tles
    val launches = dataManager.launches.launches

    // HUD — src/hud.js intelligence HUD + AI summary
    private val _hudText = MutableStateFlow("GOD'S EYE VIEW — NO PLACE LEFT BEHIND")
    val hudText: StateFlow<String> = _hudText

    // Câmera — src/camera.js
    private val _camera = MutableStateFlow(GodEyeCamera(LatLng(20.0,0.0), zoom=2f))
    val camera: StateFlow<GodEyeCamera> = _camera

    // Estilo — src/styles/* e ui.js StyleManager (1-7)
    private val _style = MutableStateFlow(0) // 0=NORMAL,1=CRT,2=NVG,3=FLIR, etc.
    val style: StateFlow<Int> = _style

    init {
        dataManager.startAll(viewModelScope, aisKey = null)
    }

    fun toggleLayer(id: LayerId) {
        _layers.update { if (it.contains(id)) it - id else it + id }
    }
    fun setStyle(idx: Int) { _style.value = idx.coerceIn(0,6) }
    fun flyTo(latLng: LatLng, zoom: Float = 12f) { _camera.value = GodEyeCamera(latLng, zoom) }
    fun resetGlobe() { _camera.value = GodEyeCamera(LatLng(20.0,0.0), zoom=2f, tilt=0f) }
    fun cockpit(flightPos: LatLng, heading: Float) { _camera.value = GodEyeCamera(flightPos, zoom=16f, tilt=67f, bearing=heading) }

    override fun onCleared() { dataManager.stopAll(); super.onCleared() }
}
