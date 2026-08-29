package com.godseye.view.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.godseye.view.data.FlightState
import com.godseye.view.data.EarthquakeFeature
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// Mapa OSM 100% offline-capable sem API key — fallback quando Google Maps fica branco
// Equivale ao OSM do web (mapStackController.js) — garante que app nunca fica inutilizável

@Composable
fun OsmFallbackMap(
    modifier: Modifier = Modifier,
    target: GeoPoint,
    zoom: Double = 3.0,
    flights: List<FlightState> = emptyList(),
    quakes: List<EarthquakeFeature> = emptyList(),
    onMapClick: (GeoPoint) -> Unit = {}
) {
    val ctx = LocalContext.current
    // Osmdroid precisa de user agent
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = "gods-eye-android/2.0"
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    }

    var mapView by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(target, zoom) {
        mapView?.controller?.apply {
            setZoom(zoom)
            animateTo(target)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { c ->
            MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK) // OSM padrão — sem key, sem branco
                setMultiTouchControls(true)
                controller.setZoom(zoom)
                controller.setCenter(target)
                overlays.add(CompassOverlay(c, InternalCompassOrientationProvider(c), this))
                val loc = MyLocationNewOverlay(GpsMyLocationProvider(c), this)
                loc.enableMyLocation()
                overlays.add(loc)
                setOnClickListener { _, _ -> false }
                mapView = this
            }
        },
        update = { mv ->
            // Atualiza markers — igual GlobeScreen mas sem Google dependency
            mv.overlays.removeIf { it is Marker }
            flights.take(300).forEach { f ->
                val lat = f.latitude ?: return@forEach
                val lon = f.longitude ?: return@forEach
                val m = Marker(mv).apply {
                    position = GeoPoint(lat, lon)
                    title = f.callsign ?: f.icao24
                    snippet = "${f.originCountry ?: ""} ${f.baroAltitude?.toInt() ?: 0}m"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    rotation = (f.trueTrack?.toFloat() ?: 0f)
                }
                mv.overlays.add(m)
            }
            quakes.forEach { q ->
                val m = Marker(mv).apply {
                    position = GeoPoint(q.lat, q.lon)
                    title = "M${q.mag} ${q.place}"
                }
                mv.overlays.add(m)
            }
            mv.invalidate()
        }
    )
}
