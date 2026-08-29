package com.godseye.view.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.godseye.view.data.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun OsmFallbackMap(
    modifier: Modifier = Modifier,
    target: GeoPoint,
    zoom: Double = 3.0,
    flights: List<FlightState> = emptyList(),
    quakes: List<EarthquakeFeature> = emptyList(),
    fires: List<FireDetection> = emptyList(),
    vessels: List<VesselPosition> = emptyList(),
    radio: List<RadioStation> = emptyList(),
    bikeshare: List<BikeshareStation> = emptyList(),
    installs: List<MilitaryInstallation> = emptyList(),
    onMapClick: (GeoPoint) -> Unit = {}
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = "gods-eye-android/2.0"
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    LaunchedEffect(target, zoom) {
        mapView?.controller?.apply { setZoom(zoom); animateTo(target) }
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { c ->
            MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(zoom)
                controller.setCenter(target)
                overlays.add(CompassOverlay(c, InternalCompassOrientationProvider(c), this))
                val loc = MyLocationNewOverlay(GpsMyLocationProvider(c), this)
                loc.enableMyLocation()
                overlays.add(loc)
                mapView = this
            }
        },
        update = { mv ->
            mv.overlays.removeIf { it is Marker }
            flights.take(300).forEach { f ->
                val lat = f.latitude ?: return@forEach
                val lon = f.longitude ?: return@forEach
                mv.overlays.add(Marker(mv).apply { position = GeoPoint(lat, lon); title = f.callsign ?: f.icao24; rotation = f.trueTrack?.toFloat() ?: 0f; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) })
            }
            quakes.forEach { q -> mv.overlays.add(Marker(mv).apply { position = GeoPoint(q.lat, q.lon); title = "M${q.mag} ${q.place}" }) }
            fires.take(500).forEach { f -> mv.overlays.add(Marker(mv).apply { position = GeoPoint(f.lat, f.lon); title = "FIRE" }) }
            vessels.take(200).forEach { v -> mv.overlays.add(Marker(mv).apply { position = GeoPoint(v.lat, v.lon); title = v.shipName ?: v.mmsi }) }
            radio.take(200).forEach { r -> mv.overlays.add(Marker(mv).apply { position = GeoPoint(r.lat, r.lon); title = r.name }) }
            bikeshare.take(100).forEach { b -> mv.overlays.add(Marker(mv).apply { position = GeoPoint(b.lat, b.lon); title = b.name }) }
            installs.take(100).forEach { ins -> mv.overlays.add(Marker(mv).apply { position = GeoPoint(ins.lat, ins.lon); title = ins.name }) }
            mv.invalidate()
        }
    )
}
