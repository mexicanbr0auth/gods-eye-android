package com.godseye.view.ui

import android.Manifest
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.godseye.view.data.LayerId
import com.godseye.view.data.MapKeyProvider
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GlobeScreen(vm: GlobeViewModel = viewModel(), onRequestKeys: () -> Unit, dataStoreGoogleKey: String? = null) {
    val ctx = LocalContext.current
    val camera by vm.camera.collectAsState()
    val flights by vm.flights.collectAsState()
    val quakes by vm.quakes.collectAsState()
    val layers by vm.layers.collectAsState()
    val style by vm.style.collectAsState()
    val camPosState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(camera.target, camera.zoom)
    }
    var mapLoaded by remember { mutableStateOf(false) }

    val manifestKey = remember { MapKeyProvider.getManifestKey(ctx) }
    // CORREÇÃO BRANCO: só manifest conta para Google 3D — DataStore sozinho não faz Maps SDK funcionar (limitação SDK), então não troca para Google branco
    val hasManifestGoogleKey = MapKeyProvider.isValidGoogleKey(manifestKey)
    // Mostra estado salvo mas mantém OSM até rebuild
    val hasGoogleKeyForUi = MapKeyProvider.isValidGoogleKey(dataStoreGoogleKey ?: manifestKey)
    var googleMapFailed by remember { mutableStateOf(false) }
    val useGoogleMap = hasManifestGoogleKey && !googleMapFailed

    LaunchedEffect(dataStoreGoogleKey, manifestKey, googleMapFailed) {
        Log.d("GodEye/Map", "manifestValid=$hasManifestGoogleKey dsValid=${MapKeyProvider.isValidGoogleKey(dataStoreGoogleKey)} useGoogle=$useGoogleMap failed=$googleMapFailed manifest=${manifestKey?.take(8)} ds=${dataStoreGoogleKey?.take(8)}")
    }
    // Se Google demorar >5s sem onMapLoaded, fallback para OSM automaticamente (evita branco infinito)
    LaunchedEffect(useGoogleMap) {
        if (useGoogleMap) {
            kotlinx.coroutines.delay(6000)
            if (!mapLoaded) {
                Log.w("GodEye/Map", "GoogleMap timeout — fallback OSM")
                googleMapFailed = true
            }
        }
    }
    LaunchedEffect(camera) {
        try {
            camPosState.animate(CameraUpdateFactory.newCameraPosition(
                CameraPosition(camera.target, camera.zoom, camera.tilt, camera.bearing)
            ), 1200)
        } catch (e: Exception) { Log.w("GodEye/Camera", "animate failed", e) }
    }

    val locPerm = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GOD'S EYE VIEW", fontSize = 14.sp, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF060A14), titleContentColor = Color(0xFF00E5FF)),
                actions = {
                    IconButton(onClick = { vm.resetGlobe() }) { Icon(Icons.Default.Public, "Reset Globe", tint = Color.White) }
                    IconButton(onClick = onRequestKeys) { Icon(Icons.Default.VpnKey, "Chaves", tint = Color.White) }
                }
            )
        },
        bottomBar = {
            LazyRow(Modifier.background(Color(0xCC060A14)).padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LayerId.entries) { id ->
                    val on = layers.contains(id)
                    FilterChip(
                        selected = on, onClick = { vm.toggleLayer(id) },
                        label = { Text(id.name, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF00E5FF))
                    )
                }
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("NORMAL","CRT","NVG","FLIR","NOIR","SNOW","TACTICAL").forEachIndexed { i, _ ->
                    SmallFloatingActionButton(
                        onClick = { vm.setStyle(i) },
                        containerColor = if (style==i) Color(0xFF00E5FF) else Color(0xFF1A2340)
                    ) { Text("$i", fontSize = 10.sp, color = Color.White) }
                }
                FloatingActionButton(onClick = {
                    if (locPerm.status.isGranted) { } else locPerm.launchPermissionRequest()
                }, containerColor = Color(0xFF00E5FF)) {
                    Icon(Icons.Default.MyLocation, "Location")
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF060A14))) {

            // CRÍTICO: Google Maps com key inválida/dummy fica BRANCO mesmo com TileOverlay — OSM (Osmdroid) é base garantida sem Play Services
            if (useGoogleMap) {
                val mapProps = MapProperties(
                    mapType = if (style==4) MapType.NORMAL else MapType.HYBRID,
                    isBuildingEnabled = true,
                    isTrafficEnabled = layers.contains(LayerId.TRAFFIC)
                )
                val mapUi = MapUiSettings(zoomControlsEnabled = false, compassEnabled = true, myLocationButtonEnabled = false, tiltGesturesEnabled = true)
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = camPosState,
                    properties = mapProps, uiSettings = mapUi,
                    onMapLoaded = { mapLoaded = true; Log.d("GodEye/Map", "GoogleMap loaded OK") },
                    onMapLongClick = { latLng -> vm.flyTo(latLng, 14f) }
                ) {
                    if (layers.contains(LayerId.FLIGHTS)) {
                        flights.take(500).forEach { f ->
                            val lat = f.latitude ?: return@forEach
                            val lon = f.longitude ?: return@forEach
                            val pos = LatLng(lat, lon)
                            Marker(
                                state = MarkerState(position = pos),
                                title = f.callsign ?: f.icao24,
                                snippet = "${f.originCountry ?: ""} alt ${f.baroAltitude?.toInt() ?: 0}m ${f.velocity?.toInt() ?: 0}kt",
                                icon = BitmapDescriptorFactory.defaultMarker(if (f.onGround) BitmapDescriptorFactory.HUE_ORANGE else BitmapDescriptorFactory.HUE_CYAN),
                                rotation = (f.trueTrack ?: 0.0).toFloat(), flat = true,
                                onClick = { vm.cockpit(pos, (f.trueTrack ?: 0.0).toFloat()); true }
                            )
                        }
                    }
                    if (layers.contains(LayerId.EARTHQUAKES)) {
                        quakes.forEach { q ->
                            Circle(center = LatLng(q.lat, q.lon), radius = (q.mag*20000).coerceAtLeast(5000.0),
                                fillColor = Color(0x66FF3D00), strokeColor = Color(0xFFFF3D00), strokeWidth = 2f)
                        }
                    }
                }
            } else {
                // Fallback 100% nativo sem Google — nunca fica branco
                OsmFallbackMap(
                    modifier = Modifier.fillMaxSize(),
                    target = GeoPoint(camera.target.latitude, camera.target.longitude),
                    zoom = camera.zoom.toDouble(),
                    flights = flights,
                    quakes = quakes,
                    onMapClick = { gp -> vm.flyTo(com.google.android.gms.maps.model.LatLng(gp.latitude, gp.longitude), 14f) }
                )
            }

            // Banner inteligente: explica branco e mostra ação certa
            if (!hasManifestGoogleKey) {
                Card(
                    Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (hasGoogleKeyForUi) Color(0xFF2E7D32) else Color(0xFFB71C1C))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (hasGoogleKeyForUi && !hasManifestGoogleKey) {
                            Text("Chave salva (DataStore) — OSM ativo até rebuild", color = Color.White, fontSize = 12.sp)
                            Text("Você salvou GOOGLE key, ela está persistida. Mas Google Maps SDK só lê do AndroidManifest no BUILD — precisa recompilar com secret para 3D. OSM já funciona 100% sem ficar branco.", color = Color(0xFFC8E6C9), fontSize = 10.sp)
                        } else {
                            Text("OSM ativo — sem Google API Key no build", color = Color.White, fontSize = 12.sp)
                            Text("Google 3D Photorealistic precisa de GOOGLE_MAPS_API_KEY no BUILD (secret Actions). Sem isso Google fica BRANCO — por isso usamos OSM nativo garantido.", color = Color(0xFFFFCDD2), fontSize = 10.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onRequestKeys, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = if (hasGoogleKeyForUi) Color(0xFF2E7D32) else Color(0xFFB71C1C))) { Text(if (hasGoogleKeyForUi) "Trocar chave" else "Configurar chave", fontSize = 11.sp) }
                            Text(if (mapLoaded || !useGoogleMap) "OSM OK" else "OSM carregando…", color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterVertically))
                        }
                    }
                }
            } else if (googleMapFailed) {
                Card(
                    Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF6C00))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Google Maps falhou — fallback OSM", color = Color.White, fontSize = 12.sp)
                        Text("Key existe mas Google não carregou (Play Services / billing / pacote SHA). Usando OSM.", color = Color.White, fontSize = 10.sp)
                    }
                }
            }

            Column(Modifier.align(Alignment.TopStart).padding(top = if (!hasManifestGoogleKey || googleMapFailed) 140.dp else 12.dp).padding(start = 12.dp).background(Color(0xAA060A14)).padding(8.dp)) {
                Text("ACTIVE STYLE: ${listOf("NORMAL","CRT","NVG","FLIR","NOIR","SNOW","TACTICAL")[style]}", color = Color(0xFF00E5FF), fontSize = 10.sp)
                Text("FLIGHTS ${flights.size}  QUAKES ${quakes.size}  ${if (useGoogleMap) "GOOGLE 3D" else "OSM"}${if (googleMapFailed) " (FALLBACK)" else ""}", color = Color.White, fontSize = 9.sp)
                Text("NO PLACE LEFT BEHIND", color = Color(0x66FFFFFF), fontSize = 8.sp, letterSpacing = 2.sp)
            }
        }
    }
}
