package com.godseye.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var appConfig: AppConfig

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Se localização foi concedida, injeta no JS
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            injectLocationPermission(true)
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appConfig = AppConfig(this)

        // Primeira execução: pede Google Maps Key se não estiver configurada
        if (appConfig.googleMapsKey == null && !appConfig.hasSeenOnboarding) {
            showKeySetupDialog()
        }

        webView = findViewById(R.id.webview)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        swipeRefresh.setOnRefreshListener { webView.reload() }
        swipeRefresh.setColorSchemeResources(
            android.R.color.holo_blue_dark,
            android.R.color.holo_green_dark,
            android.R.color.holo_orange_dark
        )

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString + " GodEyeAndroid/1.0"
            // WebGL & hardware accel
            setSupportZoom(false)
        }

        // Permitir geolocalização HTML5
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                // Checa permissão Android; se não tem, pede
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, true)
                } else {
                    // pede e por enquanto nega; quando conceder, recarrega
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                    callback.invoke(origin, false, false)
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                // Para mic (voice) – garante que WebView peça RECORD_AUDIO
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val needsMic = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    val needsVideo = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    if (needsMic || needsVideo) {
                        val perms = mutableListOf<String>()
                        if (needsMic) perms += Manifest.permission.RECORD_AUDIO
                        // RECORD_AUDIO é o suficiente para WebRTC voice
                        if (perms.any { ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED }) {
                            permissionLauncher.launch(perms.toTypedArray())
                            request.deny()
                            return
                        }
                    }
                    request.grant(request.resources)
                }
            }

            override fun onShowFileChooser(
                wv: WebView?,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                return try {
                    fileChooserLauncher.launch(params.createIntent())
                    true
                } catch (e: Exception) {
                    fileChooserCallback = null
                    false
                }
            }

            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                // Log útil em debug
                if (BuildConfig.DEBUG) {
                    android.util.Log.d("GodEye/WebConsole", "${cm.message()} -- ${cm.sourceId()}:${cm.lineNumber()}")
                }
                return super.onConsoleMessage(cm)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // Mantém navegação interna no WebView; externo abre no browser
                return if (isInternalUrl(url)) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Não foi possível abrir: $url", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                swipeRefresh.isRefreshing = false
                injectKeys()
                injectAndroidBridge()
                injectLocationPermission(hasLocationPermission())
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Injeta o mais cedo possível (localStorage antes do app ler)
                view.evaluateJavascript(appConfig.toJsInjection(), null)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    // Tenta mostrar erro amigável se file:// falhar (dist não copiado)
                    if (BuildConfig.DEBUG) {
                        android.util.Log.e("GodEye/WebView", "Error ${error.errorCode}: ${error.description} at ${request.url}")
                    }
                }
            }
        }

        // Bridge JS <-> Kotlin: expõe capacidades nativas
        webView.addJavascriptInterface(AndroidBridge(), "Android")

        // Carrega app
        val startUrl = resolveStartUrl(intent)
        webView.loadUrl(startUrl)

        // Pede permissões não críticas de forma lazy
        maybeRequestLocation()
    }

    private fun resolveStartUrl(intent: Intent?): String {
        // Deep link (share link) -> repassa como query para o app web
        intent?.data?.let { uri ->
            val encoded = Uri.encode(uri.toString())
            // O app web sabe ler ?android_deeplink=
            return assetUrl() + "?android_deeplink=$encoded"
        }

        // Query inicial preservada (ex: estilo, layers)
        intent?.getStringExtra("startUrl")?.let { return it }

        return assetUrl()
    }

    private fun assetUrl(): String {
        // Tenta carregar do assets/dist (gerado por syncWebAssets). Fallback: remote se configurado.
        // Se dist não existir, WebView mostrará erro – o syncWebAssets garante que em release exista.
        return "file:///android_asset/dist/index.html"
    }

    private fun isInternalUrl(url: String): Boolean {
        return url.startsWith("file:///android_asset/") ||
            url.contains("cesium") ||
            url.contains("google") ||
            url.contains("openstreetmap") ||
            url.startsWith("https://") && (
                url.contains("overpass") ||
                url.contains("opensky") ||
                url.contains("celestrak") ||
                url.contains("tomtom") ||
                url.contains("firms") ||
                url.contains("radio-browser") ||
                url.contains("aisstream") ||
                url.contains("godseye.local")
            ) ||
            // Para dev: se apontar para localhost dev server
            url.startsWith("http://10.0.2.2") ||
            url.startsWith("http://localhost")
        // Heurística conservadora: mantém https no WebView por padrão para mapas
        // Se quiser restringir, descomente abaixo para só manter file://
        // return url.startsWith("file://")
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun maybeRequestLocation() {
        if (!hasLocationPermission() && shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION).not()) {
            // Não pede agressivamente no onCreate; deixa WebView pedir via geolocation prompt
        }
    }

    private fun injectLocationPermission(granted: Boolean) {
        webView.evaluateJavascript(
            """
            (function(){
              window.__ANDROID_LOCATION_GRANTED__ = ${if (granted) "true" else "false"};
              window.dispatchEvent(new CustomEvent('android:location', {detail:{granted:${if (granted) "true" else "false"}}}));
            })();
            """.trimIndent(), null
        )
    }

    private fun injectKeys() {
        webView.evaluateJavascript(appConfig.toJsInjection(), null)
    }

    private fun showKeySetupDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "GOOGLE_MAPS_API_KEY (obrigatória)"
            setPadding(48, 32, 48, 16)
        }
        val input2 = android.widget.EditText(this).apply {
            hint = "CESIUM_ION_TOKEN (opcional, para Bing)"
            setPadding(48, 16, 48, 32)
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(input)
            addView(input2)
            // se já tem valor, mostra
            input.setText(appConfig.googleMapsKey ?: "")
            input2.setText(appConfig.cesiumIonToken ?: "")
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Configurar chaves")
            .setMessage("O globo 3D precisa de GOOGLE_MAPS_API_KEY (Google Map Tiles API). Sem ela o mapa fica preto. Você pode colar agora ou depois em Menu > Configurar chaves.\n\nCesium Ion é opcional (Bing).")
            .setView(container)
            .setPositiveButton("Salvar") { _, _ ->
                val g = input.text.toString().trim()
                val c = input2.text.toString().trim()
                if (g.isNotEmpty()) appConfig.googleMapsKey = g
                if (c.isNotEmpty()) appConfig.cesiumIonToken = c
                appConfig.hasSeenOnboarding = true
                if (g.isNotEmpty() && ::webView.isInitialized) {
                    injectKeys()
                    webView.reload()
                }
            }
            .setNegativeButton("Depois") { _, _ ->
                appConfig.hasSeenOnboarding = true
            }
            .setNeutralButton("Ajuda") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mexicanbr0auth/gods-eye-android#chaves")))
                } catch (_: Exception) {}
                appConfig.hasSeenOnboarding = true
            }
            .setCancelable(false)
            .show()
    }

    fun showKeyDialogFromJs() {
        runOnUiThread { showKeySetupDialog() }
    }

    private fun injectAndroidBridge() {
        webView.evaluateJavascript(
            """
            (function(){
              window.__IS_ANDROID__ = true;
              window.__ANDROID_BRIDGE__ = true;
              if(window.Android && window.Android.onWebReady) {
                try{ window.Android.onWebReady(); }catch(e){}
              }
            })();
            """.trimIndent(), null
        )
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun getVersion(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface
        fun getPlatform(): String = "android"

        @JavascriptInterface
        fun hasLocationPermission(): Boolean = this@MainActivity.hasLocationPermission()

        @JavascriptInterface
        fun requestLocation() {
            runOnUiThread {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        @JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(send, "Compartilhar"))
            }
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun configureKeys() {
            showKeyDialogFromJs()
        }

        @JavascriptInterface
        fun getGoogleMapsKey(): String? = appConfig.googleMapsKey

        @JavascriptInterface
        fun getCesiumToken(): String? = appConfig.cesiumIonToken

        @JavascriptInterface
        fun setGoogleMapsKey(key: String) {
            appConfig.googleMapsKey = key
            runOnUiThread {
                injectKeys()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_keys -> { showKeySetupDialog(); true }
            R.id.action_reload -> { webView.reload(); true }
            R.id.action_share -> {
                webView.evaluateJavascript("(function(){return window.location.href;})();") { url ->
                    val clean = url?.trim('"') ?: assetUrl()
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, clean)
                    }
                    startActivity(Intent.createChooser(send, "Compartilhar"))
                }
                true
            }
            R.id.action_about -> {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("God's Eye View Android")
                    .setMessage("Spy-satellite simulator — dados reais.\n\nVersão: ${BuildConfig.VERSION_NAME}\nWebView: ${webView.settings.userAgentString}\n\nChaves via Menu > Configurar chaves.\nOriginal: github.com/bilawalsidhu/gods-eye-view")
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Re-carrega deep link dentro do WebView sem recriar Activity
        val newUrl = resolveStartUrl(intent)
        if (newUrl != assetUrl()) {
            webView.loadUrl(newUrl)
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        webView.pauseTimers()
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
