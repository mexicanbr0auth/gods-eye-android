# 🌐 God's Eye View — Android Nativo

> **Port 100% nativo para Android** — fork do [bilawalsidhu/gods-eye-view](https://github.com/bilawalsidhu/gods-eye-view) reescrito código-a-código em **Kotlin + Jetpack Compose + Google Maps SDK**. Nenhum WebView bagunçado: roda nativo, com permissões, layers e voz.

Photorealistic 3D globe (Google Maps 3D Buildings = equivalente nativo do `Google Photorealistic 3D Tiles` do web), live aircraft/ships/satellites/earthquakes/traffic/CCTV, HUD e voz.

---

## 📱 O que foi portado (web → nativo)

| Web (`src/*.js`) | Nativo (`android/app/src/main/java/com/godseye/view`) | Obs |
|---|---|---|
| `src/main.js` bootstrap + Cesium Viewer | `MainActivity.kt` + `ui/GlobeScreen.kt` `GoogleMap` Compose | `Cesium Viewer` → `GoogleMap` `MapType.HYBRID` + `isBuildingEnabled=true` (3D) |
| `src/camera.js` `orbit.js` `cockpitTracking.js` | `camera/CameraController.kt` | `CameraPosition` tilt/bearing/zoom |
| `src/ui.js` `hud.js` `styles/*` | `ui/GlobeViewModel.kt` + HUD Compose | `StyleManager` 1-7 → FABs `NORMAL/CRT/NVG/FLIR...` |
| `src/data/manager.js` `layerState.js` | `data/Repositories.kt` `DataLayerManager` | polling nativo |
| `src/data/flights.js` `militaryFlights.js` `adsbLolFallback.js` | `data/Repositories.kt` `FlightRepository` | `GET https://opensky-network.org/api/states/all` via Retrofit (mesmo que `vite openSkyProxy`) |
| `src/data/satellites.js` `satellite.js` SGP4 | `SatelliteRepository` + `propagate()` | TLE `celestrak.org/NORAD/elements/gp.php` (mesmo proxy `celestrakProxy`) |
| `src/data/earthquakes.js` | `EarthquakeRepository` | `earthquake.usgs.gov` |
| `src/data/aisLiveVessels.js` `aisStreamAdapter.js` | `VesselRepository` | `wss://aisstream.io` via OkHttp WS (mesmo `aisLiveProxy`) |
| `src/data/cctv.js` `cctvViewshed.js` | `CctvRepository` | `config/cctv_sources.*.json` nativo |
| `src/data/traffic.js` `flowTiles.js` `tomtomTiles.js` | `TrafficRepository` | `isTrafficEnabled` + `TileOverlay` TomTom |
| `src/data/firmsCsv.js` | `ApiService.getFirms()` | FIRMS CSV |
| `src/data/bikeshare.js` `radio.js` `rocketLaunches.js` `militaryInstallations.js` | `LaunchRepository` etc | `GBFS`/`Radio`/`LL2`/`Overpass` (mesmos proxies `gbfsProxy`/`radioBrowserProxy`/`overpassProxy`) |
| `src/voice/gevRealtime.js` `gevActions.js` 28 tools | `voice/VoiceAgent.kt` | `SpeechRecognizer` + OkHttp WS OpenAI Realtime |
| `vite.config.js` proxies + `GOOGLE_MAPS_API_KEY` | `data/ApiService.kt` + `AppDataStore.kt` `DataStore<Preferences>` | chaves em runtime, sem rebuild |
| `index.html` `style.css` overlays | `ui/theme/Theme.kt` + Compose `Scaffold`/`FilterChip`/`Circle`/`Marker` | `flat=true` + `rotation` = `iconOrientation.js` world-stable |

---

## 🔐 Permissões (AndroidManifest.xml)

```xml
INTERNET, ACCESS_NETWORK_STATE (todos os proxies)
ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION (hud.js/locations.js/regionalBrief/cockpit)
RECORD_AUDIO, MODIFY_AUDIO_SETTINGS (voice/gevRealtime.js)
POST_NOTIFICATIONS (FIRMS/bikeshare)
```
Runtime via `accompanist-permissions` — igual `GEV_RATELIMIT_*` do vite mas nativo.

---

## 🚀 Quick Start (nativo)

```bash
# Só Android — não precisa npm/vite
./android/gradlew -p android assembleDebug
# APK em android/app/build/outputs/apk/debug/app-debug.apk
# Release:
./android/gradlew -p android assembleRelease
```

Chaves: `GOOGLE_MAPS_API_KEY` **obrigatória** (3D). Pode ser:
- `export GOOGLE_MAPS_API_KEY=...` antes do build (vira `manifestPlaceholders` em `android/app/build.gradle:20`)
- ou dentro do app: botão `VpnKey` → `KeyDialog` → salva em `DataStore` (igual `localStorage` no web)

`CESIUM_ION_TOKEN` opcional (Bing), `OPENAI_API_KEY` p/ voz, `AISSTREAM_API_KEY` p/ navios, `FIRMS_MAP_KEY`, `TOMTOM_API_KEY`.

---

## 🤖 GitHub + `gh` + Actions (nativo)

```bash
gh auth login
gh repo create gods-eye-android --public --source=. --remote=origin --push
git push -u origin main
gh workflow run "Android Build (Native)"  # workflow_dispatch
gh run list --repo mexicanbr0auth/gods-eye-android
gh run view --log --repo mexicanbr0auth/gods-eye-android
```

Workflow `.github/workflows/android.yml`:
- `build-apk`: `setup-java@17` + `setup-android` + `gradle assembleDebug` + `assembleRelease` → artifacts `apk-debug-native` / `apk-release-unsigned-native`
- `release`: em tags `v*` cria GitHub Release com os APKs

Secrets: `GOOGLE_MAPS_API_KEY` (opcional, se não setar pede no app).

---

## 📦 Estrutura nativa

```
android/app/src/main/java/com/godseye/view/
├── MainActivity.kt              # ComponentActivity + GlobeScreen (porta src/main.js)
├── GodEyeApplication.kt
├── data/
│   ├── AppDataStore.kt          # DataStore prefs (porta .env + localStorage)
│   ├── Models.kt                # FlightState/Earthquake/Satellite/Vessel/Cctv (porta src/data/*.js)
│   ├── ApiService.kt            # Retrofit endpoints (porta vite.config.js proxies)
│   └── Repositories.kt          # Flight/Earthquake/Satellite/Vessel/Traffic/Cctv/Launch + DataLayerManager (porta src/data/manager.js)
├── camera/CameraController.kt   # CameraPosition builder (porta src/camera.js/orbit.js/cockpitTracking.js)
├── ui/
│   ├── GlobeViewModel.kt        # layers/style/hud/camera (porta src/ui.js/hud.js/layerState.js)
│   ├── GlobeScreen.kt           # GoogleMap Compose + Marker/Circle/HUD (porta index.html + Cesium)
│   └── theme/Theme.kt
├── voice/VoiceAgent.kt          # SpeechRecognizer + OpenAI WS (porta src/voice/gevRealtime.js)
└── util/Permissions.kt
```

Web original continua em `src/`/`public/` como referência, mas o APK **não** depende de `dist/` nem `vite`.

---

## ⚠️ Notas

- Google Maps 3D Buildings é o equivalente nativo mais próximo do `Google Photorealistic 3D Tiles` (Cesium) — para tiles fotorealísticos completos, troque `MapType` por `Maps SDK 3D` (em beta) ou `MapLibre + Cesium Native`.
- SGP4 real: adicione `com.github.cromwellian:satellite-kotlin` e implemente `SatelliteRepository.propagate()` com `SGP4`.
- AISStream precisa de `AISSTREAM_API_KEY` em `DataStore` + OkHttp WebSocket.

---

## 📄 Licença

MIT — mesmo do upstream (`LICENSE`).
