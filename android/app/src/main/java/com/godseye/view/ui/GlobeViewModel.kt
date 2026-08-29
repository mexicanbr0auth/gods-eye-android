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
    // 9 layers restantes
    val radio = dataManager.radio.stations
    val bikeshare = dataManager.bikeshare.stations
    val fires = dataManager.fires.fires
    val vesselsWs = dataManager.vesselsWs.vessels
    val installs = dataManager.installations.installs

    // Cockpit — src/cockpitTracking.js
    private val _cockpit = MutableStateFlow(CockpitState())
    val cockpit: StateFlow<CockpitState> = _cockpit
    // Detecção overlay — src/detection/*
    private val _detectionEnabled = MutableStateFlow(false)
    val detectionEnabled: StateFlow<Boolean> = _detectionEnabled
    // HUD — src/hud.js
    private val _hudText = MutableStateFlow("GOD'S EYE VIEW — NO PLACE LEFT BEHIND")
    val hudText: StateFlow<String> = _hudText

    private val _camera = MutableStateFlow(GodEyeCamera(LatLng(20.0,0.0), zoom=2f))
    val camera: StateFlow<GodEyeCamera> = _camera
    private val _style = MutableStateFlow(0)
    val style: StateFlow<Int> = _style

    init {
        // Inicia todos 13 layers (keys opcionais via DataStore se tiver)
        dataManager.startAll(viewModelScope, aisKey = null, firmsKey = null, ctx = null)
    }
    fun initWithContext(ctx: android.content.Context, aisKey: String?, firmsKey: String?) {
        dataManager.startAll(viewModelScope, aisKey, firmsKey, ctx)
    }

    fun toggleLayer(id: LayerId) { _layers.update { if (it.contains(id)) it - id else it + id } }
    fun setStyle(idx: Int) { _style.value = idx.coerceIn(0,6) }
    fun flyTo(latLng: LatLng, zoom: Float = 12f) { _camera.value = GodEyeCamera(latLng, zoom) }
    fun resetGlobe() { _camera.value = GodEyeCamera(LatLng(20.0,0.0), zoom=2f, tilt=0f); _cockpit.value = CockpitState() }
    fun cockpit(flightPos: LatLng, heading: Float) { _camera.value = GodEyeCamera(flightPos, zoom=16f, tilt=67f, bearing=heading); _cockpit.value = CockpitState(true, heading=heading) }
    fun exitCockpit() { _cockpit.value = CockpitState(); resetGlobe() }
    fun toggleDetection() { _detectionEnabled.value = !_detectionEnabled.value }
    fun shareLink(): String {
        val c = _camera.value
        return "godseye://view?lat=${c.target.latitude}&lon=${c.target.longitude}&z=${c.zoom}&style=${_style.value}&layers=${_layers.value.joinToString(",")}"
    }

    override fun onCleared() { dataManager.stopAll(); super.onCleared() }
}
