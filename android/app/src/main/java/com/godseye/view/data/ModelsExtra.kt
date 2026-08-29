package com.godseye.view.data

import kotlinx.serialization.Serializable

// Modelos faltantes — porta fiel de src/data/*.js

@Serializable data class RadioStation(
    val id: String, val name: String, val lat: Double, val lon: Double,
    val url: String? = null, val country: String? = null, val bitrate: Int? = null
)

@Serializable data class BikeshareFeed(val networkId: String, val stations: List<BikeshareStation>)

@Serializable data class FireDetection(
    val lat: Double, val lon: Double,
    val brightness: Double? = null, val confidence: String? = null,
    val acqTime: String? = null, val satellite: String? = null
)

@Serializable data class MilitaryInstallation(
    val id: String, val name: String, val lat: Double, val lon: Double, val kind: String
)

@Serializable data class BundledFeature(
    val id: String, val name: String, val lat: Double, val lon: Double,
    val kind: String // dam | cable | datacenter
)

// Estilos — src/styles/*
enum class MapStyle(val label: String, val mapTypeDesc: String) {
    NORMAL("NORMAL","HYBRID"), CRT("CRT","NORMAL+filter"), NVG("NVG","NORMAL+green"), FLIR("FLIR","NORMAL+ironbow"), NOIR("NOIR","NORMAL+bw"), SNOW("SNOW","NORMAL+white"), TACTICAL("TACTICAL","HYBRID+tactical")
}

// Cockpit — src/cockpitTracking.js / cockpitMath.js
data class CockpitState(val isActive: Boolean = false, val targetIcao: String? = null, val heading: Float = 0f, val altitude: Double? = null)

// Detecção overlay — src/detection/*
data class DetectionBox(val id: String, val lat: Double, val lon: Double, val label: String)
