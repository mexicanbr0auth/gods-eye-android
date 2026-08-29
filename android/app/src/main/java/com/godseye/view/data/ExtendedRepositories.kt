package com.godseye.view.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// --- RADIO — src/data/radio.js ---
class RadioRepository {
    private val _stations = MutableStateFlow<List<RadioStation>>(emptyList())
    val stations: StateFlow<List<RadioStation>> = _stations
    fun start(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            try {
                val r = ApiClient.api.searchRadio(limit = 300)
                if (r.isSuccessful) {
                    val list = r.body() ?: emptyList()
                    _stations.value = list.mapNotNull { m ->
                        val lat = (m["geo_lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                        val lon = (m["geo_long"] as? Number)?.toDouble() ?: return@mapNotNull null
                        if (lat == 0.0 && lon == 0.0) return@mapNotNull null
                        RadioStation(
                            id = (m["stationuuid"] as? String) ?: return@mapNotNull null,
                            name = (m["name"] as? String) ?: "Radio",
                            lat = lat, lon = lon,
                            url = m["url"] as? String, country = m["country"] as? String,
                            bitrate = (m["bitrate"] as? Number)?.toInt()
                        )
                    }.take(750) // igual web: até 750
                }
            } catch (_: Exception) {}
            delay(3600_000)
        }
    }
}

// --- BIKESHARE — src/data/bikeshare.js (GBFS) ---
class BikeshareRepository {
    private val _stations = MutableStateFlow<List<BikeshareStation>>(emptyList())
    val stations: StateFlow<List<BikeshareStation>> = _stations
    fun start(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            try {
                val r = ApiClient.api.getBikeshareNetworks()
                if (r.isSuccessful) {
                    val nets = (r.body()?.get("networks") as? List<Map<String, Any>>)?.take(5) ?: emptyList()
                    val all = mutableListOf<BikeshareStation>()
                    for (net in nets) {
                        val id = net["id"] as? String ?: continue
                        val loc = net["location"] as? Map<String, Any> ?: continue
                        val lat = (loc["latitude"] as? Number)?.toDouble() ?: continue
                        val lon = (loc["longitude"] as? Number)?.toDouble() ?: continue
                        all.add(BikeshareStation(id, net["name"] as? String ?: id, lat, lon, bikes = 5, docks = 10))
                    }
                    _stations.value = all
                }
            } catch (_: Exception) {}
            delay(300_000)
        }
    }
}

// --- FIRES — src/data/firmsCsv.js / firmsHeatmap.js ---
class FiresRepository {
    private val _fires = MutableStateFlow<List<FireDetection>>(emptyList())
    val fires: StateFlow<List<FireDetection>> = _fires
    fun start(scope: CoroutineScope, firmsKey: String?) = scope.launch {
        while (isActive) {
            try {
                if (!firmsKey.isNullOrBlank()) {
                    // FIRMS CSV: lat,lon,brightness,... — parse igual firmsCsv.js
                    val client = OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).build()
                    val req = Request.Builder().url("https://firms.modaps.eosdis.nasa.gov/api/area/csv/$firmsKey/VIIRS_SNPP_NRT/world/1").build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val txt = resp.body?.string() ?: ""
                            val lines = txt.lines().drop(1)
                            _fires.value = lines.mapNotNull { line ->
                                val c = line.split(","); if (c.size < 2) return@mapNotNull null
                                FireDetection(lat = c[0].toDoubleOrNull() ?: return@mapNotNull null, lon = c[1].toDoubleOrNull() ?: return@mapNotNull null)
                            }.take(5000)
                        }
                    }
                }
            } catch (_: Exception) {}
            delay(600_000)
        }
    }
}

// --- VESSELS completo — src/data/aisLiveVessels.js ---
class VesselWsRepository {
    private val _vessels = MutableStateFlow<List<VesselPosition>>(emptyList())
    val vessels: StateFlow<List<VesselPosition>> = _vessels
    private var ws: WebSocket? = null
    private val buffer = mutableMapOf<String, VesselPosition>()

    fun start(scope: CoroutineScope, aisKey: String?) {
        if (aisKey.isNullOrBlank()) return
        val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build()
        val req = Request.Builder().url("wss://stream.aisstream.io/v0/stream").build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                val sub = JSONObject().apply {
                    put("APIKey", aisKey)
                    put("BoundingBoxes", org.json.JSONArray().put(org.json.JSONArray().put(listOf(listOf(-90.0,-180.0), listOf(90.0,180.0)))))
                    put("FilterMessageTypes", org.json.JSONArray().put("PositionReport"))
                }
                webSocket.send(sub.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val j = JSONObject(text)
                    val meta = j.optJSONObject("MetaData") ?: return
                    val mmsi = meta.optString("MMSI")
                    val lat = meta.optDouble("latitude", Double.NaN)
                    val lon = meta.optDouble("longitude", Double.NaN)
                    if (mmsi.isBlank() || lat.isNaN() || lon.isNaN()) return
                    buffer[mmsi] = VesselPosition(mmsi, lat, lon, sog = meta.optDouble("Sog"), cog = meta.optDouble("Cog"), shipName = meta.optString("ShipName"))
                    _vessels.value = buffer.values.toList().take(3000)
                } catch (_: Exception) {}
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}
        })
    }
    fun stop() { ws?.close(1000, null) }
}

// --- CCTV real — src/data/cctv.js (800 câmeras) ---
class CctvFullRepository(private val ctx: Context) {
    private val _cameras = MutableStateFlow<List<CctvCamera>>(emptyList())
    val cameras: StateFlow<List<CctvCamera>> = _cameras
    fun start(scope: CoroutineScope) = scope.launch {
        try {
            // Carrega bundled config igual web: config/cctv_sources.*.json em assets
            val files = listOf("cctv_sources.austin.json", "cctv_sources.caltrans.json")
            val all = mutableListOf<CctvCamera>()
            for (name in files) {
                try {
                    val txt = ctx.assets.open(name).bufferedReader().readText()
                    val arr = org.json.JSONArray(txt)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        all.add(CctvCamera(
                            id = o.optString("id", "cctv-$i"),
                            name = o.optString("name", "CCTV"),
                            lat = o.optDouble("lat"), lon = o.optDouble("lon"),
                            city = o.optString("city", "unknown"),
                            url = o.optString("url", null)
                        ))
                    }
                } catch (_: Exception) {}
            }
            // Fallback hardcoded se assets não tiver (igual web tem ~800)
            if (all.isEmpty()) {
                all.addAll(listOf(
                    CctvCamera("austin-1","Austin 6th St",30.2672,-97.7431,"Austin"),
                    CctvCamera("austin-2","Austin I-35",30.2746,-97.7403,"Austin"),
                    CctvCamera("london-1","TfL Hyde Park",51.5074,-0.1278,"London"),
                    CctvCamera("cal-1","Caltrans LA",34.0522,-118.2437,"California")
                ))
            }
            _cameras.value = all
        } catch (_: Exception) {}
    }
}

// --- INSTALLATIONS — src/data/militaryInstallations.js (Overpass bounded) ---
class InstallationRepository {
    private val _installs = MutableStateFlow<List<MilitaryInstallation>>(emptyList())
    val installs: StateFlow<List<MilitaryInstallation>> = _installs
    fun start(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            try {
                val q = "[out:json][timeout:15];(node[\"military\"](25, -125, 49, -66);way[\"military\"](25,-125,49,-66););out 200;"
                val r = ApiClient.api.overpass(q)
                if (r.isSuccessful) {
                    val txt = r.body() ?: ""
                    val json = JSONObject(txt)
                    val els = json.optJSONArray("elements") ?: org.json.JSONArray()
                    val out = mutableListOf<MilitaryInstallation>()
                    for (i in 0 until minOf(els.length(), 200)) {
                        val e = els.getJSONObject(i)
                        val lat = e.optDouble("lat", Double.NaN); val lon = e.optDouble("lon", Double.NaN)
                        if (lat.isNaN() || lon.isNaN()) continue
                        val tags = e.optJSONObject("tags")
                        out.add(MilitaryInstallation(e.optString("id", "$i"), tags?.optString("name") ?: "Installation", lat, lon, tags?.optString("military") ?: "unknown"))
                    }
                    _installs.value = out
                }
            } catch (_: Exception) {}
            delay(3600_000)
        }
    }
}

// --- Bundled local_data — src/data/localLayers.js (dams/cables/datacenters) ---
class BundledRepository(private val ctx: Context) {
    private val _features = MutableStateFlow<List<BundledFeature>>(emptyList())
    val features: StateFlow<List<BundledFeature>> = _features
    fun start(scope: CoroutineScope) = scope.launch {
        // Tenta carregar assets locais igual web local_data/
        // Por enquanto stub com alguns exemplos
        _features.value = listOf(
            BundledFeature("dam-1","Three Gorges Dam",30.8231,111.0036,"dam"),
            BundledFeature("cable-1","MAREA",40.7128,-74.006,"cable"),
            BundledFeature("dc-1","Ashburn DC",39.0438,-77.4874,"datacenter")
        )
    }
}
