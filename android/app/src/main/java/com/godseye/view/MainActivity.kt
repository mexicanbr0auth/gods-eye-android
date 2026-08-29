package com.godseye.view

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.godseye.view.data.AppDataStore
import com.godseye.view.ui.GlobeScreen
import com.godseye.view.ui.GlobeViewModel
import com.godseye.view.ui.theme.GodEyeTheme
import kotlinx.coroutines.launch

// Nativo — não usa mais WebView. Cada módulo web foi portado para Kotlin:
// main.js -> setContent + GlobeScreen, hud.js -> HUD Compose, camera.js -> CameraController,
// data/*.js -> Repositories.kt, voice/gevRealtime.js -> voice/VoiceAgent.kt (stub)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GodEyeTheme {
                val ctx = LocalContext.current
                val app = ctx.applicationContext as GodEyeApplication
                var showKeys by remember { mutableStateOf(false) }
                // ViewModel nativo
                val vm: GlobeViewModel = viewModel()

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GlobeScreen(vm = vm, onRequestKeys = { showKeys = true })
                }

                if (showKeys) KeyDialog(app.dataStore, onDismiss = { showKeys = false })
            }
        }
    }
}

@Composable
fun KeyDialog(store: AppDataStore, onDismiss: () -> Unit) {
    var google by remember { mutableStateOf("") }
    var cesium by remember { mutableStateOf("") }
    var openai by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar chaves") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GOOGLE_MAPS_API_KEY (obrigatória para 3D) — equivale a .env web", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(google, { google = it }, label = { Text("Google Maps API Key") }, singleLine = true)
                OutlinedTextField(cesium, { cesium = it }, label = { Text("CESIUM_ION_TOKEN (opcional Bing)") }, singleLine = true)
                OutlinedTextField(openai, { openai = it }, label = { Text("OPENAI_API_KEY (voz)") }, singleLine = true)
                Text("As chaves ficam em DataStore (nativo) — igual localStorage no web. Sem rebuild.", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    if (google.isNotBlank()) store.setGoogleKey(google)
                    if (cesium.isNotBlank()) store.setCesium(cesium)
                    if (openai.isNotBlank()) store.setOpenAI(openai)
                    store.setOnboard(true)
                    onDismiss()
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}
