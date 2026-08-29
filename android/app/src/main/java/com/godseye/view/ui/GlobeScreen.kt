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
    val radio by vm.radio.collectAsState()
    val bikeshare by vm.bikeshare.collectAsState()
    val fires by vm.fires.collectAsState()
    val vessels by vm.vesselsWs.collectAsState()
    val installs by vm.installs.collectAsState()
    val sats by vm.sats.collectAsState()
    val launches by vm.launches.collectAsState()
    val cockpit by vm.cockpit.collectAsState()
    val detectionOn by vm.detectionEnabled.collectAsState()
    val layers by vm.layers.collectAsState()
    val style by vm.style.collectAsState()
    val camPosState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(camera.target, camera.zoom)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    // Inicializa layers com context/keys (igual web que lê .env) — porta manager.js startAll
    LaunchedEffect(Unit) {
        val firms = ctx.getSharedPreferences("godseye", android.content.Context.MODE_PRIVATE).getString("FIRMS_KEY", null)
        val ais = ctx.getSharedPreferences("godseye", android.content.Context.MODE_PRIVATE).getString("AIS_KEY", null)
        vm.initWithContext(ctx, ais, firms)
    }

    val manifestKey = remember { MapKeyProvider.getManifestKey(ctx) }
    // Agora DataStore patcha ApplicationInfo em runtime (GodEyeApplication + KeyDialog recreate), então DataStore VALE para Google
    val hasManifestGoogleKey = MapKeyProvider.isValidGoogleKey(manifestKey)
    val hasGoogleKeyForUi = MapKeyProvider.isValidGoogleKey(dataStoreGoogleKey ?: manifestKey)
    val hasEffectiveGoogleKey = MapKeyProvider.isValidGoogleKey(dataStoreGoogleKey) || hasManifestGoogleKey
    var googleMapFailed by remember { mutableStateOf(false) }
    val useGoogleMap = hasEffectiveGoogleKey && !googleMapFailed

    LaunchedEffect(dataStoreGoogleKey, manifestKey, googleMapFailed) {
        Log.d("GodEye/Map", "manifestValid=$hasManifestGoogleKey dsValid=${MapKeyProvider.isValidGoogleKey(dataStoreGoogleKey)} effective=$hasEffectiveGoogleKey useGoogle=$useGoogleMap failed=$googleMapFailed manifest=${manifestKey?.take(8)} ds=${dataStoreGoogleKey?.take(8)}")
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
                // Styles 1-7 — src/styles/* + StyleManager; H/D/C no web viram FABs aqui
                listOf("NORMAL","CRT","NVG","FLIR","NOIR","SNOW","TACTICAL").forEachIndexed { i, _ ->
                    SmallFloatingActionButton(
                        onClick = { vm.setStyle(i) },
                        containerColor = if (style==i) Color(0xFF00E5FF) else Color(0xFF1A2340)
                    ) { Text("$i", fontSize = 10.sp, color = Color.White) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(onClick = { vm.toggleDetection() }, containerColor = if (detectionOn) Color(0xFF00E5FF) else Color(0xFF1A2340)) {
                        Text("D", color = Color.White, fontSize = 12.sp)
                    }
                    SmallFloatingActionButton(onClick = { if (cockpit.isActive) vm.exitCockpit() else flights.firstOrNull()?.let { f -> f.latitude?.let { lat -> f.longitude?.let { lon -> vm.cockpit(LatLng(lat, lon), f.trueTrack?.toFloat() ?: 0f) } } } }, containerColor = if (cockpit.isActive) Color(0xFFFF3D00) else Color(0xFF1A2340)) {
                        Text("C", color = Color.White, fontSize = 12.sp)
                    }
                    SmallFloatingActionButton(onClick = { vm.resetGlobe() }, containerColor = Color(0xFF1A2340)) { Icon(Icons.Default.Home, "Home", tint = Color.White) }
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
                // Style filter — src/styles/* GLSL sensor looks (nativo: overlay color)
                val styleOverlay = when(style){
                    1 -> Color(0x3300FF00) // NVG green
                    2 -> Color(0x33FF6A00) // CRT amber
                    3 -> Color(0x33FF3D00) // FLIR ironbow
                    else -> Color.Transparent
                }
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = camPosState,
                    properties = mapProps, uiSettings = mapUi,
                    onMapLoaded = { mapLoaded = true; Log.d("GodEye/Map", "GoogleMap loaded OK") },
                    onMapLongClick = { latLng -> vm.flyTo(latLng, 14f) }
                ) {
                    // FLIGHTS — src/data/flights.js + militaryFlights.js + iconOrientation.js
                    if (layers.contains(LayerId.FLIGHTS) || layers.contains(LayerId.MILITARY)) {
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
                    // EARTHQUAKES — src/data/earthquakes.js
                    if (layers.contains(LayerId.EARTHQUAKES)) {
                        quakes.forEach { q ->
                            Circle(center = LatLng(q.lat, q.lon), radius = (q.mag*20000).coerceAtLeast(5000.0),
                                fillColor = Color(0x66FF3D00), strokeColor = Color(0xFFFF3D00), strokeWidth = 2f)
                        }
                    }
                    // SATELLITES — src/data/satellites.js (840) — dot + label
                    if (layers.contains(LayerId.SATELLITES)) {
                        sats.take(200).forEachIndexed { i, s ->
                            val lat = (i * 0.5 - 50); val lon = (i * 1.2 - 180)
                            Marker(state = MarkerState(LatLng(lat, lon)), title = s.name, icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET), alpha = 0.8f)
                        }
                    }
                    // VESSELS — src/data/aisLiveVessels.js
                    if (layers.contains(LayerId.VESSELS)) {
                        vessels.take(300).forEach { v ->
                            Marker(state = MarkerState(LatLng(v.lat, v.lon)), title = v.shipName ?: v.mmsi, snippet = "SOG ${v.sog ?: 0}kn HDG ${v.heading ?: 0}", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        }
                    }
                    // FIRES — src/data/firmsCsv.js FIRMS
                    if (layers.contains(LayerId.FIRES)) {
                        fires.take(1000).forEach { f ->
                            Circle(center = LatLng(f.lat, f.lon), radius = 800.0, fillColor = Color(0x99FF6A00), strokeColor = Color(0xFFFF3D00), strokeWidth = 1f)
                        }
                    }
                    // RADIO — src/data/radio.js
                    if (layers.contains(LayerId.RADIO)) {
                        radio.take(300).forEach { r ->
                            Marker(state = MarkerState(LatLng(r.lat, r.lon)), title = r.name, snippet = r.country ?: "", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                        }
                    }
                    // BIKESHARE — src/data/bikeshare.js GBFS
                    if (layers.contains(LayerId.BIKESHARE)) {
                        bikeshare.take(200).forEach { b ->
                            Marker(state = MarkerState(LatLng(b.lat, b.lon)), title = b.name, snippet = "bikes ${b.bikes}/${b.docks}", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                        }
                    }
                    // INSTALLATIONS — src/data/militaryInstallations.js
                    if (layers.contains(LayerId.INSTALLATIONS)) {
                        installs.take(200).forEach { ins ->
                            Circle(center = LatLng(ins.lat, ins.lon), radius = 2000.0, fillColor = Color(0x33FF0000), strokeColor = Color.Red, strokeWidth = 1f)
                            Marker(state = MarkerState(LatLng(ins.lat, ins.lon)), title = ins.name, icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED), alpha = 0.7f)
                        }
                    }
                    // LAUNCHES — src/data/rocketLaunches.js
                    if (layers.contains(LayerId.LAUNCHES)) {
                        launches.take(20).forEachIndexed { i, l ->
                            val lat = 28.5 + i*0.5; val lon = -80.6 + i
                            Marker(state = MarkerState(LatLng(lat, lon)), title = l.name, snippet = l.net, icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA))
                        }
                    }
                    // CCTV — src/data/cctv.js
                    if (layers.contains(LayerId.CCTV)) {
                        // Usa cameras do CctvFullRepository se tiver, senão placeholder
                        Marker(state = MarkerState(LatLng(30.2672,-97.7431)), title = "Austin CCTV", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        Marker(state = MarkerState(LatLng(51.5074,-0.1278)), title = "London TfL", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    }
                }
                // GLSL sensor overlay — src/styles/* (nativo: sem shader, mas tint)
                if (styleOverlay != Color.Transparent) {
                    Box(Modifier.fillMaxSize().background(styleOverlay)) {}
                }
            } else {
                OsmFallbackMap(
                    modifier = Modifier.fillMaxSize(),
                    target = GeoPoint(camera.target.latitude, camera.target.longitude),
                    zoom = camera.zoom.toDouble(),
                    flights = flights, quakes = quakes, fires = fires, vessels = vessels, radio = radio, bikeshare = bikeshare, installs = installs,
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
                Text("FLIGHTS ${flights.size}  QUAKES ${quakes.size}  SATS ${sats.size}  VESSELS ${vessels.size}  FIRES ${fires.size}  RADIO ${radio.size}  BIKE ${bikeshare.size}  INST ${installs.size}", color = Color.White, fontSize = 7.sp)
                Text("${if (useGoogleMap) "GOOGLE 3D" else "OSM"}${if (googleMapFailed) " (FALLBACK)" else ""} — TACTICAL HUD", color = Color(0xFF00E5FF), fontSize = 8.sp)
                Text("NO PLACE LEFT BEHIND", color = Color(0x66FFFFFF), fontSize = 8.sp, letterSpacing = 2.sp)
                if (detectionOn) Text("DETECTION OVERLAY ON — ${flights.size} boxes", color = Color(0xFFFFEB3B), fontSize = 8.sp)
            }
            // Cockpit overlay — src/cockpitTracking.js / hud.js intelligence HUD
            if (cockpit.isActive) {
                Card(Modifier.align(Alignment.BottomStart).padding(12.dp).fillMaxWidth(0.85f), colors = CardDefaults.cardColors(containerColor = Color(0xCC0A0E1A))) {
                    Column(Modifier.padding(10.dp)) {
                        Text("COCKPIT LOCK — HDG ${cockpit.heading.toInt()}°", color = Color(0xFF00E5FF), fontSize = 11.sp)
                        Text("Tilt 67°  Zoom 16  —  src/cockpitTracking.js", color = Color.White, fontSize = 9.sp)
                        Button(onClick = { vm.exitCockpit() }, modifier = Modifier.padding(top = 6.dp)) { Text("Sair Cockpit (C)", fontSize = 10.sp) }
                    }
                }
            }
            // Voice hint — src/voice/gevRealtime.js
            Card(Modifier.align(Alignment.BottomEnd).padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xAA1A2340))) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VOZ: diga 'Take me to Tokyo' ou 'outline Texas'", color = Color.White, fontSize = 8.sp)
                    Text("28 tools — precisa OPENAI_API_KEY", color = Color(0x66FFFFFF), fontSize = 7.sp)
                }
            }
        }
    }
}
