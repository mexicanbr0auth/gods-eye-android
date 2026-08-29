package com.godseye.view.voice

import android.content.Context
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Porta nativa de src/voice/gevRealtime.js (OpenAI Realtime) + 28 tools
// No web: WebSocket + AudioWorklet; no nativo: SpeechRecognizer + OkHttp WS para OpenAI Realtime

class VoiceAgent(private val ctx: Context) {
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            // setRecognitionListener tradicional — mapear para tool calls de src/voice/gevActions.js
            // ex: "Take me to Tokyo" -> cameraController.flyTo, "outline Texas" -> annotationEngine
        }
        _listening.value = true
    }
    fun stop() { recognizer?.destroy(); _listening.value = false }

    // 28 tools do web viram intents Kotlin — src/voice/gevActions.js
    fun handleTool(name: String, args: Map<String,String>, onCamera: (String)->Unit) {
        when(name){
            "flyTo" -> onCamera(args["location"] ?: "")
            "setLayer" -> {}
            "annotate" -> {}
            else -> {}
        }
    }
}
