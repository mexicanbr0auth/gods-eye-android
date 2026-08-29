package com.godseye.view.data

import kotlinx.serialization.Serializable

// Porta de src/data/*.js — modelos nativos espelhando os JSON das APIs

@Serializable data class FlightState(
    val icao24: String,
    val callsign: String? = null,
    val originCountry: String? = null,
    val longitude: Double? = null,
    val latitude: Double? = null,
    val baroAltitude: Double? = null,
    val geoAltitude: Double? = null,
    val velocity: Double? = null,
    val trueTrack: Double? = null,
    val verticalRate: Double? = null,
    val onGround: Boolean = false,
    val squawk: String? = null,
    val spi: Boolean = false,
    val category: Int? = null // src/data/aircraftClass.js
)
@Serializable data class OpenSkyResponse(val time: Long, val states: List<List<String>>? = null)

@Serializable data class EarthquakeFeature(
    val id: String, val mag: Double, val place: String,
    val time: Long, val lat: Double, val lon: Double, val depth: Double
)

@Serializable data class SatelliteTle(val name: String, val line1: String, val line2: String, val group: String = "active")

@Serializable data class VesselPosition(
    val mmsi: String, val lat: Double, val lon: Double,
    val sog: Double? = null, val cog: Double? = null, val heading: Int? = null,
    val shipName: String? = null, val shipType: Int? = null
)

@Serializable data class CctvCamera(
    val id: String, val name: String, val lat: Double, val lon: Double,
    val city: String, val url: String? = null, val thumbnail: String? = null
)

@Serializable data class LaunchMission(
    val id: String, val name: String, val net: String, val status: String,
    val pad: String? = null, val agency: String? = null
)

@Serializable data class BikeshareStation(val id: String, val name: String, val lat: Double, val lon: Double, val bikes: Int, val docks: Int)

enum class LayerId { FLIGHTS, MILITARY, VESSELS, SATELLITES, EARTHQUAKES, TRAFFIC, CCTV, RADIO, BIKESHARE, FIRES, LAUNCHES, INSTALLATIONS }
