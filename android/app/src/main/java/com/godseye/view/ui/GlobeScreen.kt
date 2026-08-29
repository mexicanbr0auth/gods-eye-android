package com.godseye.view.ui

import android.Manifest
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.godseye.view.data.LayerId

// Porta nativa de index.html + src/ui.js + src/hud.js + style.css
// No web: Cesium Viewer + #cesiumContainer; no nativo: GoogleMap Compose com buildings 3D + tilt

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GlobeScreen(vm: GlobeViewModel = viewModel(), onRequestKeys: () -> Unit) {
    val camera by vm.camera.collectAsState()
    val flights by vm.flights.collectAsState()
    val quakes by vm.quakes.collectAsState()
    val layers by vm.layers.collectAsState()
    val style by vm.style.collectAsState()
    val camPosState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(camera.target, camera.zoom)
    }
    LaunchedEffect(camera) {
        camPosState.animate(CameraUpdateFactory.newCameraPosition(
            CameraPosition(camera.target, camera.zoom, camera.tilt, camera.bearing)
        ), 1200)
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
            // Layer chips — espelha src/ui.js panels + src/data/layerState.js
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
                // Style 1-7 — src/styles/* + StyleManager (H/D/C shortcuts no web viram FABs)
                listOf("NORMAL","CRT","NVG","FLIR","NOIR","SNOW","TACTICAL").forEachIndexed { i, name ->
                    SmallFloatingActionButton(
                        onClick = { vm.setStyle(i) },
                        containerColor = if (style==i) Color(0xFF00E5FF) else Color(0xFF1A2340)
                    ) { Text("$i", fontSize = 10.sp, color = Color.White) }
                }
                FloatingActionButton(onClick = {
                    if (locPerm.status.isGranted) {
                        // cockpitTracking.js — centra no usuário
                    } else locPerm.launchPermissionRequest()
                }, containerColor = Color(0xFF00E5FF)) {
                    Icon(Icons.Default.MyLocation, "Location")
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val mapProps = MapProperties(
                mapType = when(style){
                    4 -> MapType.NORMAL // Noir via style
                    else -> MapType.HYBRID // Photorealistic mais próximo do Google 3D Tiles web
                },
                isBuildingEnabled = true, // 3D buildings — equivale a Google Photorealistic 3D Tiles no web
                isTrafficEnabled = layers.contains(LayerId.TRAFFIC)
            )
            val mapUi = MapUiSettings(zoomControlsEnabled = false, compassEnabled = true, myLocationButtonEnabled = false, tiltGesturesEnabled = true)

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camPosState,
                properties = mapProps, uiSettings = mapUi,
                onMapClick = { /* src/worldFocus.js */ },
                onMapLongClick = { latLng -> vm.flyTo(latLng, 14f) }
            ) {
                // Flights — src/data/flights.js + iconOrientation.js (heading real)
                if (layers.contains(LayerId.FLIGHTS)) {
                    flights.take(500).forEach { f ->
                        val pos = LatLng(f.latitude!!, f.longitude!!)
                        Marker(
                            state = MarkerState(position = pos),
                            title = f.callsign ?: f.icao24,
                            snippet = "${f.originCountry ?: ""} alt ${f.baroAltitude?.toInt() ?: 0}m ${f.velocity?.toInt() ?: 0}kt",
                            icon = BitmapDescriptorFactory.defaultMarker(
                                if (f.onGround) BitmapDescriptorFactory.HUE_ORANGE else BitmapDescriptorFactory.HUE_CYAN
                            ),
                            rotation = (f.trueTrack ?: 0.0).toFloat(),
                            flat = true, // heading real — igual iconOrientation.js world-stable
                            onClick = { vm.cockpit(pos, (f.trueTrack ?: 0.0).toFloat()); true }
                        )
                    }
                }
                // Earthquakes — src/data/earthquakes.js
                if (layers.contains(LayerId.EARTHQUAKES)) {
                    quakes.forEach { q ->
                        Circle(center = LatLng(q.lat, q.lon), radius = (q.mag*20000).coerceAtLeast(5000.0),
                            fillColor = Color(0x66FF3D00), strokeColor = Color(0xFFFF3D00), strokeWidth = 2f)
                    }
                }
                // Satellites — src/data/satellites.js (840 objetos) — mostra como markers; orbit ring via Polyline
                // Vessels — src/data/aisLiveVessels.js
                // CCTV viewshed — src/data/cctvViewshed.js — Circle + Polygon
                // Traffic — via MapProperties.isTrafficEnabled + TileOverlay TomTom se key

                // HUD overlay — src/hud.js intelligence HUD
                // No web é DOM overlay; aqui Compose overlay
            }

            // HUD nativo — topo: estilo + contador (igual #style-indicator, #hud)
            Column(Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xAA060A14)).padding(8.dp)) {
                Text("ACTIVE STYLE: ${listOf("NORMAL","CRT","NVG","FLIR","NOIR","SNOW","TACTICAL")[style]}", color = Color(0xFF00E5FF), fontSize = 10.sp)
                Text("FLIGHTS ${flights.size}  QUAKES ${quakes.size}", color = Color.White, fontSize = 9.sp)
                Text("NO PLACE LEFT BEHIND", color = Color(0x66FFFFFF), fontSize = 8.sp, letterSpacing = 2.sp)
            }

            // Voice FAB — src/voice/gevRealtime.js (OpenAI Realtime) — nativo: SpeechRecognizer + OkHttp WS
            // Placeholder: mostra botão que abre VoiceScreen (não implementado aqui)
        }
    }
}
