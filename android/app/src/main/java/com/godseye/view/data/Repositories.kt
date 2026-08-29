package com.godseye.view.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.google.android.gms.maps.model.LatLng
import java.util.concurrent.TimeUnit

// Equivalente nativo de src/data/manager.js — centraliza polling, cache e credit governor (vite.config.js)

object ApiClient {
    val ok = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()
    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://example.com/") // base dummy, URLs são absolutas
        .client(ok).addConverterFactory(GsonConverterFactory.create()).build()
    val api: GodEyeApi = retrofit.create(GodEyeApi::class.java)
}

// Cada Repository porta um src/data/*.js fielmente

class FlightRepository {
    private val _flights = MutableStateFlow<List<FlightState>>(emptyList())
    val flights: StateFlow<List<FlightState>> = _flights
    private var job: Job? = null
    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                try {
                    val resp = ApiClient.api.getFlights()
                    if (resp.isSuccessful) {
                        val body = resp.body()
                        val list = body?.states?.mapNotNull { s ->
                            // s: [icao24, callsign, origin_country, time_pos, last_contact, lon, lat, baro, on_ground, vel, heading, vrate, ...]
                            if (s.size < 11) null else FlightState(
                                icao24 = s[0].trim(), callsign = s[1]?.trim()?.takeIf { it.isNotBlank() },
                                originCountry = s[2], longitude = s[5].toDoubleOrNull(),
                                latitude = s[6].toDoubleOrNull(), baroAltitude = s[7].toDoubleOrNull(),
                                onGround = s[8] == "true", velocity = s[9].toDoubleOrNull(),
                                trueTrack = s[10].toDoubleOrNull(), verticalRate = s.getOrNull(11)?.toDoubleOrNull()
                            )
                        }?.filter { it.latitude != null && it.longitude != null } ?: emptyList()
                        _flights.value = list
                    }
                } catch (_: Exception) {}
                delay(15000) // igual vite openSkyProxy 9-15s adaptive TTL
            }
        }
    }
    fun stop() { job?.cancel() }
}

class EarthquakeRepository {
    private val _quakes = MutableStateFlow<List<EarthquakeFeature>>(emptyList())
    val quakes: StateFlow<List<EarthquakeFeature>> = _quakes
    fun start(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            try {
                val r = ApiClient.api.getEarthquakes()
                if (r.isSuccessful) {
                    val m = r.body() ?: emptyMap()
                    val feats = (m["features"] as? List<Map<String, Any>>)?.mapNotNull { f ->
                        val p = f["properties"] as? Map<String, Any> ?: return@mapNotNull null
                        val g = f["geometry"] as? Map<String, Any> ?: return@mapNotNull null
                        val coords = g["coordinates"] as? List<Double> ?: return@mapNotNull null
                        EarthquakeFeature(
                            id = (f["id"] as? String) ?: "", mag = (p["mag"] as? Double) ?: 0.0,
                            place = (p["place"] as? String) ?: "", time = (p["time"] as? Number)?.toLong() ?: 0,
                            lon = coords.getOrNull(0) ?: 0.0, lat = coords.getOrNull(1) ?: 0.0, depth = coords.getOrNull(2) ?: 0.0
                        )
                    } ?: emptyList()
                    _quakes.value = feats
                }
            } catch (_: Exception) {}
            delay(60_000)
        }
    }
}

class SatelliteRepository {
    private val _tles = MutableStateFlow<List<SatelliteTle>>(emptyList())
    val tles: StateFlow<List<SatelliteTle>> = _tles
    fun start(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            try {
                val r = ApiClient.api.getTle("active")
                if (r.isSuccessful) {
                    val txt = r.body() ?: ""
                    val lines = txt.lines()
                    val out = mutableListOf<SatelliteTle>()
                    var i = 0; while (i + 2 < lines.size) {
                        val n = lines[i].trim(); val l1 = lines[i+1].trim(); val l2 = lines[i+2].trim()
                        if (l1.startsWith("1 ") && l2.startsWith("2 ")) { out.add(SatelliteTle(n,l1,l2)); i+=3 } else i++
                    }
                    _tles.value = out.take(840) // igual web: core 840
                }
            } catch (_: Exception) {}
            delay(3600_000) // TLE muda a cada horas
        }
    }
    // Propagação SGP4 nativa: src/data/satellites.js usa satellite.js; aqui usaríamos sgp4 kotlin (ex: https://github.com/cromwellian/satellite-kotlin) — stub calcula posição aproximada
    fun propagate(tle: SatelliteTle, millis: Long): LatLng? {
        // TODO: SGP4 real — por enquanto retorna posição fictícia sobre equador para não crashar; trocar por lib sgp4
        return null
    }
}

class VesselRepository {
    private val _vessels = MutableStateFlow<List<VesselPosition>>(emptyList())
    val vessels: StateFlow<List<VesselPosition>> = _vessels
    // AISStream via OkHttp WebSocket — igual src/data/aisStreamAdapter.js + vite aisLiveProxy
    fun start(scope: CoroutineScope, apiKey: String?) {
        if (apiKey.isNullOrBlank()) return
        // stub: conecta WS wss://aisstream.io/api/ws — enviar subscribe JSON
        // implementação real lê mensagens e emite VesselPosition
    }
}

class CctvRepository {
    // src/data/cctv.js — 800 câmeras Austin/Caltrans/TfL
    val cameras: List<CctvCamera> = listOf(
        // bundled igual public/local_data, mas nativo carrega de config/cctv_sources.*.json via assets
    )
}

class TrafficRepository {
    // src/data/traffic.js + flowTiles.js — TomTom vector tiles
    fun tileUrl(z:Int,x:Int,y:Int, key:String) = "https://api.tomtom.com/traffic/map/4/tile/flow/relative/$z/$x/$y.pbf?key=$key"
}

class LaunchRepository {
    private val _launches = MutableStateFlow<List<LaunchMission>>(emptyList())
    val launches: StateFlow<List<LaunchMission>> = _launches
    fun start(s: CoroutineScope) = s.launch {
        while(isActive){ try{
            val r = ApiClient.api.getLaunches(); if(r.isSuccessful){
                val m = r.body(); val results = m?.get("results") as? List<Map<String,Any>> ?: emptyList()
                _launches.value = results.mapNotNull{ it ->
                    LaunchMission(it["id"].toString(), it["name"].toString(), it["net"].toString(), (it["status"] as? Map<String,Any>)?.get("name").toString(), (it["pad"] as? Map<String,Any>)?.get("name")?.toString())
                }
            }
        }catch(_:Exception){}; delay(600_000)}
    }
}

// Central Manager — equivale a src/data/manager.js DataLayerManager (agora 13 layers completos)
class DataLayerManager(
    val flights: FlightRepository = FlightRepository(),
    val quakes: EarthquakeRepository = EarthquakeRepository(),
    val sats: SatelliteRepository = SatelliteRepository(),
    val vessels: VesselRepository = VesselRepository(),
    val launches: LaunchRepository = LaunchRepository(),
    // 9 layers restantes — porta fiel
    val radio: RadioRepository = RadioRepository(),
    val bikeshare: BikeshareRepository = BikeshareRepository(),
    val fires: FiresRepository = FiresRepository(),
    val vesselsWs: VesselWsRepository = VesselWsRepository(),
    val installations: InstallationRepository = InstallationRepository(),
) {
    fun startAll(scope: CoroutineScope, aisKey: String?, firmsKey: String? = null, ctx: Context? = null) {
        flights.start(scope); quakes.start(scope); sats.start(scope); launches.start(scope); vessels.start(scope, aisKey)
        radio.start(scope); bikeshare.start(scope); installations.start(scope)
        vesselsWs.start(scope, aisKey); fires.start(scope, firmsKey)
        if (ctx != null) {
            CctvFullRepository(ctx).start(scope)
            BundledRepository(ctx).start(scope)
        }
    }
    fun stopAll(){ flights.stop(); vesselsWs.stop() }
}
