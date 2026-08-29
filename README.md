# 🌐 God's Eye View — Android

> **Spy-satellite simulator compatível com Android** — fork do projeto original [bilawalsidhu/gods-eye-view](https://github.com/bilawalsidhu/gods-eye-view) adaptado para rodar como app nativo Android (WebView) + build web Vite.

Photorealistic 3D globe, live aircraft/ships/satellites/terremotos/tráfego/câmeras, com wrapper Android e **builds automáticos via GitHub Actions**.

---

## 📱 O que foi adaptado para Android

- **Wrapper nativo** em `android/` com `MainActivity.kt` (WebView hardware-accelerated, WebGL, Geolocation, file chooser, SwipeRefresh)
- **Bridge JS ↔ Kotlin** (`window.Android.*`): platform detection, share, toast, permissões de localização/mic
- **Gestão de chaves em runtime**: `GOOGLE_MAPS_API_KEY` (obrigatória) + `CESIUM_ION_TOKEN` (opcional) salvas em `SharedPreferences` e injetadas via `localStorage`/`window` — não precisa rebuildar o APK para trocar de chave
- **Diálogo de onboarding** na primeira execução para colar a chave do Google
- **Menu nativo** (⋮): Configurar chaves / Recarregar / Compartilhar / Sobre
- **Vite `base: './'`** quando `ANDROID_BUILD=1` para `file:///android_asset/dist/` funcionar
- **Sync script** `scripts/sync-android-assets.mjs` + task Gradle `syncWebAssets` (copia `dist/` → `android/app/src/main/assets/dist/`)
- **Deep links** (`godseye://`, `https://godseye.local`) preservados como `?android_deeplink=`
- **Permissões**: `INTERNET`, `ACCESS_FINE/COARSE_LOCATION`, `RECORD_AUDIO` (voice opcional)

---

## 🚀 Quick Start (dev local)

```bash
npm install
# 1. configure chaves
cp .env.example .env  # coloque GOOGLE_MAPS_API_KEY
# 2. web puro
npm run dev -- --host localhost --port 4173
# 3. Android (gera dist + copia + builda APK debug)
npm run build:android
# requer Android SDK + JDK 17; ou use gradle wrapper
./android/gradlew -p android assembleDebug
# APK em android/app/build/outputs/apk/debug/app-debug.apk
```

### Chaves

| Chave | Onde | Obrigatória |
|---|---|---|
| `GOOGLE_MAPS_API_KEY` | Google Cloud Console → Map Tiles API | **sim** (senão o globo fica preto) |
| `CESIUM_ION_TOKEN` | cesium.com/ion | não (só p/ Bing) |
| demais (OpenAI, AISStream, FIRMS, TomTom) | `.env` | não, funcionam em runtime via proxies dev; no APK estático algumas layers usam fetch direto |

No **APK**, a chave do Google pode ser configurada **dentro do app** (Menu → Configurar chaves) sem rebuild.

---

## 🤖 GitHub + `gh` + Actions

### Criar repo e push via `gh`

```bash
gh auth login
gh repo create gods-eye-view-android --public --source=. --remote=origin --push
# ou se já tem remote:
git remote add origin https://github.com/<user>/gods-eye-view-android.git
git push -u origin main
```

O workflow `.github/workflows/android.yml` já está incluso:

- **build-web**: `npm ci` + `ANDROID_BUILD=1 npm run build` + `sync-android-assets` → artifact `dist-web`
- **build-apk**: `setup-java@17` + `setup-android` + `gradle assembleDebug` + `assembleRelease` → artifacts `apk-debug` / `apk-release-unsigned`
- **release**: em tags `v*`, cria GitHub Release com os APKs

**Secrets opcionais** no repo (Settings → Secrets → Actions):
- `GOOGLE_MAPS_API_KEY` / `CESIUM_ION_TOKEN` — se preenchidos, são baked no build; se vazios, o app pede em runtime.

Dispare manualmente: `gh workflow run "Android Build"`  
Veja logs: `gh run list` / `gh run watch`

### Build local sem `gh`

```bash
gh workflow run android.yml  # remoto
# ou local:
npm run android:assemble
```

---

## 📦 Estrutura

```
.
├── src/androidBridge.js      # shim de chaves Android ↔ web
├── scripts/sync-android-assets.mjs
├── android/
│   ├── app/build.gradle      # syncWebAssets task, minSdk 24, target 34
│   ├── app/src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/godseye/view/
│   │   │   ├── MainActivity.kt
│   │   │   ├── AppConfig.kt
│   │   │   └── GodEyeApplication.kt
│   │   └── res/{layout,values,menu,xml,mipmap-*}
│   └── gradle/wrapper/
├── .github/workflows/
│   ├── android.yml           # build web + APK
│   └── ci.yml
└── dist/ -> android/app/src/main/assets/dist/ (via sync)
```

---

## 🔐 Assinatura Release

Crie `android/keystore.properties`:

```
storeFile=/caminho/app.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

E `android/app/build.gradle` já usa `signingConfig release` se o arquivo existir.

---

## ⚠️ Notas

- `vite.config.js` tem proxies dev (`/api/*`) que **não existem** no APK estático — layers que dependem de proxy (OpenAI Realtime, AISStream WS) ficam degradados; flights/satellites/earthquakes direto via CORS continuam OK.
- Para voz completa no APK, hospede um backend proxy e aponte o WebView para ele, ou mantenha uso do app web hospedado.
- Créditos/layer docs originais em `DATA_SOURCES.md`, `SECURITY.md`.

---

## 📄 Licença

MIT — mesmo do upstream. Veja `LICENSE`.
