package com.godseye.view

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import com.godseye.view.data.MapKeyProvider
import com.godseye.view.ui.GlobeScreen
import com.godseye.view.ui.GlobeViewModel
import com.godseye.view.ui.theme.GodEyeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GodEyeTheme {
                val ctx = LocalContext.current
                val app = ctx.applicationContext as GodEyeApplication
                var showKeys by remember { mutableStateOf(false) }
                val vm: GlobeViewModel = viewModel()

                // Observa DataStore — agora persiste e reflete imediatamente
                val savedGoogle by app.dataStore.googleMapsKey.collectAsState(initial = null)
                val savedCesium by app.dataStore.cesiumToken.collectAsState(initial = null)
                val savedOpenAI by app.dataStore.openAIKey.collectAsState(initial = null)

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GlobeScreen(
                        vm = vm,
                        onRequestKeys = { showKeys = true },
                        dataStoreGoogleKey = savedGoogle
                    )
                }

                if (showKeys) KeyDialog(
                    store = app.dataStore,
                    initialGoogle = savedGoogle ?: "",
                    initialCesium = savedCesium ?: "",
                    initialOpenAI = savedOpenAI ?: "",
                    onDismiss = { showKeys = false }
                )
            }
        }
    }
}

@Composable
fun KeyDialog(
    store: AppDataStore,
    initialGoogle: String,
    initialCesium: String,
    initialOpenAI: String,
    onDismiss: () -> Unit
) {
    var google by remember(initialGoogle) { mutableStateOf(initialGoogle) }
    var cesium by remember(initialCesium) { mutableStateOf(initialCesium) }
    var openai by remember(initialOpenAI) { mutableStateOf(initialOpenAI) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // Atualiza quando DataStore muda (ex: após salvar)
    LaunchedEffect(initialGoogle, initialCesium, initialOpenAI) {
        google = initialGoogle
        cesium = initialCesium
        openai = initialOpenAI
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar chaves") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GOOGLE_MAPS_API_KEY — salva em DataStore e já aplica no Maps SDK (sem rebuild). OSM é fallback se falhar.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(google, { google = it }, label = { Text("Google Maps API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (google.isNotBlank() && !google.startsWith("AIza")) Text("⚠️ Normalmente começa com AIza… confira no Google Cloud", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(cesium, { cesium = it }, label = { Text("CESIUM_ION_TOKEN (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(openai, { openai = it }, label = { Text("OPENAI_API_KEY (voz)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Persistência: DataStore prefs (equiv. localStorage web). Salvar fecha e já atualiza banner do mapa.", style = MaterialTheme.typography.labelSmall)
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            val g = google.trim()
                            val c = cesium.trim()
                            val o = openai.trim()
                            if (g.isNotEmpty()) store.setGoogleKey(g) else if (google.isEmpty() && initialGoogle.isNotEmpty()) store.clearGoogleKey()
                            if (c.isNotEmpty()) store.setCesium(c)
                            if (o.isNotEmpty()) store.setOpenAI(o)
                            store.setOnboard(true)
                            // Patch runtime imediato: atualiza ApplicationInfo para Google Maps SDK ler sem rebuild
                            if (g.isNotEmpty() && MapKeyProvider.isValidGoogleKey(g)) {
                                try {
                                    val ai = ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA)
                                    ai.metaData.putString("com.google.android.geo.API_KEY", g)
                                    Log.d("GodEye/Keys", "Patched runtime API_KEY ${g.take(8)}… — reiniciando para aplicar")
                                } catch (e: Exception) { Log.w("GodEye/Keys", "Patch failed", e) }
                                Toast.makeText(ctx, "Chave salva! Reiniciando para ativar Google 3D…", Toast.LENGTH_LONG).show()
                                onDismiss()
                                // Recria activity para MapsInitializer reler a key
                                (ctx as? android.app.Activity)?.recreate()
                            } else {
                                Toast.makeText(ctx, if (g.isNotEmpty()) "Chaves salvas! Reinicie app para Google 3D (OSM já ativo)" else "Chaves salvas!", Toast.LENGTH_LONG).show()
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally { saving = false }
                    }
                }
            ) { Text(if (saving) "Salvando…" else "Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}
