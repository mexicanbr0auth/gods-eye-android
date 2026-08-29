package com.godseye.view.data

import retrofit2.http.*
import retrofit2.Response

// Todas as URLs são os mesmos upstreams de vite.config.js, agora chamadas direto (sem proxy dev)
// Cada método espelha um proxy: openSkyProxy, celestrakProxy, overpassProxy, etc.

interface GodEyeApi {
    // OpenSky — src/data/flights.js: /api/opensky -> https://opensky-network.org/api/states/all
    @GET("https://opensky-network.org/api/states/all")
    suspend fun getFlights(): Response<OpenSkyResponse>

    // CelesTrak — src/data/satellites.js: /api/celestrak/* -> https://celestrak.org/NORAD/elements/gp.php
    @GET("https://celestrak.org/NORAD/elements/gp.php")
    suspend fun getTle(@Query("GROUP") group: String, @Query("FORMAT") format: String = "tle"): Response<String>

    // USGS Earthquakes — src/data/earthquakes.js
    @GET("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson")
    suspend fun getEarthquakes(): Response<Map<String, Any>>

    // NASA FIRMS — src/data/firmsCsv.js -> https://firms.modaps.eosdis.nasa.gov/api/area/csv
    @GET("https://firms.modaps.eosdis.nasa.gov/api/area/csv/{key}/{source}/world/2")
    suspend fun getFirms(@Path("key") key: String, @Path("source") source: String): Response<String>

    // Launch Library 2 — src/data/rocketLaunches.js
    @GET("https://ll.thespacedevs.com/2.2.0/launch/upcoming/")
    suspend fun getLaunches(@Query("limit") limit: Int = 20): Response<Map<String, Any>>

    // Overpass — src/data/traffic.js, annotations
    @POST("https://overpass-api.de/api/interpreter")
    suspend fun overpass(@Body body: String): Response<String>

    // AISStream é WebSocket — tratado em VesselRepository via OkHttp WS (src/data/aisLiveVessels.js / aisStreamAdapter.js)
}
