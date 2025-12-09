package ar.edu.unrn.echolens

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unrn.echolens.ai.SttRepository
import ar.edu.unrn.echolens.ai.SttWsRepo
import ar.edu.unrn.echolens.ai.ws.SttWebSocketClient
import ar.edu.unrn.echolens.audio.AudioRecorder
import ar.edu.unrn.echolens.ble.BleClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SttBleViewModel : ViewModel() {

    // ==========================================
    // ⚠️ REVISA ESTA URL CADA VEZ QUE INICIES NGROK ⚠️
    // ==========================================
    private val NGROK_URL = "wss://supersensitive-eloisa-exuberant.ngrok-free.dev/ws/audio"

    private val sttRepo: SttRepository = SttWsRepo(SttWebSocketClient(NGROK_URL))
    private var bleClient: BleClient? = null
    private val audioRecorder = AudioRecorder()

    // Estados de UI
    private val _uiText = MutableStateFlow("Listo. Presiona para hablar.")
    val uiText: StateFlow<String> = _uiText

    private val _isRecordingMic = MutableStateFlow(false)
    val isRecordingMic: StateFlow<Boolean> = _isRecordingMic

    private val _connectionStatus = MutableStateFlow("Desconectado")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private var audioJob: Job? = null
    private var wsJob: Job? = null

    init {
        // Escuchar errores globalmente
        viewModelScope.launch {
            sttRepo.errors().collectLatest { error ->
                Log.e("EchoLensVM", "Error WS: $error")
                _uiText.value = "❌ Error: $error"
                _connectionStatus.value = "Error de Conexión"
                _isRecordingMic.value = false
            }
        }
    }

    // --- LÓGICA MICRÓFONO ---
    fun toggleMicRecording() {
        if (_isRecordingMic.value) {
            stopAll()
        } else {
            startMicRecording()
        }
    }

    private fun startMicRecording() {
        stopAll() // Limpieza preventiva

        _uiText.value = "🔌 Conectando al servidor..."
        _connectionStatus.value = "Conectando..."

        Log.d("EchoLensVM", "Iniciando conexión a: $NGROK_URL")

        // 🔥 CAMBIO CRÍTICO: Esperamos a que conecte ANTES de grabar
        viewModelScope.launch(Dispatchers.Main) {
            val connected = sttRepo.connect()

            if (!connected) {
                Log.e("EchoLensVM", "❌ No se pudo conectar")
                _uiText.value = "❌ No se pudo conectar al servidor"
                _connectionStatus.value = "Error de Conexión"
                return@launch
            }

            Log.d("EchoLensVM", "✅ Conexión establecida, iniciando grabación...")
            _uiText.value = "🎙️ Grabando..."
            _connectionStatus.value = "Conectado"
            _isRecordingMic.value = true

            // Ahora SÍ escuchamos respuestas
            listenToWebSocketResponses()

            // Y empezamos a grabar
            startAudioCapture()
        }
    }

    private fun startAudioCapture() {
        audioJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("EchoLensVM", "🎤 Lanzando recorder...")
                audioRecorder.startRecording()
                    .catch { e ->
                        Log.e("EchoLensVM", "❌ Crash en recorder flow: $e")
                        launch(Dispatchers.Main) {
                            _uiText.value = "Error Micrófono: ${e.localizedMessage}"
                            stopAll()
                        }
                    }
                    .collect { chunk ->
                        if (chunk.isEmpty()) {
                            Log.w("EchoLensVM", "⚠️ Chunk vacío ignorado")
                            return@collect
                        }

                        Log.v("EchoLensVM", "📤 Enviando chunk de ${chunk.size} bytes")
                        sttRepo.sendChunk(chunk)

                        launch(Dispatchers.Main) {
                            _connectionStatus.value = "Enviando Audio 🎙️"
                        }
                    }
            } catch (e: Exception) {
                Log.e("EchoLensVM", "❌ Error fatal grabando: ${e.message}")
                launch(Dispatchers.Main) {
                    _uiText.value = "Error grave: ${e.message}"
                    stopAll()
                }
            }
        }
    }

    private fun listenToWebSocketResponses() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            sttRepo.results().collectLatest { msg ->
                Log.d("EchoLensVM", "📩 Mensaje recibido: type=${msg.type}, text='${msg.text}'")

                when (msg.type) {
                    "pong" -> {
                        _connectionStatus.value = "Servidor Activo 💚"
                    }
                    "transcription" -> {
                        if (!msg.text.isNullOrBlank() && !msg.text.startsWith("[")) {
                            _uiText.value = msg.text
                            _connectionStatus.value = "Transcribiendo 📝"

                            // Reenvío a BLE si corresponde
                            if (!_isRecordingMic.value && bleClient != null) {
                                bleClient?.sendTextToEsp(msg.text)
                            }
                        } else if (msg.text?.startsWith("[") == true) {
                            // Silencio o inaudible, no mostrar pero sí loguear
                            Log.d("EchoLensVM", "🔇 ${msg.text}")
                        }
                    }
                }
            }
        }
    }

    fun stopAll() {
        Log.d("EchoLensVM", "🛑 Deteniendo todo...")
        _isRecordingMic.value = false
        audioJob?.cancel()
        wsJob?.cancel()
        audioJob = null
        wsJob = null

        sttRepo.close()
        _connectionStatus.value = "Desconectado"

        if (_uiText.value.contains("Conectando") || _uiText.value.contains("Grabando")) {
            _uiText.value = "Listo. Presiona para hablar."
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAll()
    }

    // --- LÓGICA BLE ---
    fun initBle(ctx: Context) {
        if (bleClient == null) bleClient = BleClient(ctx)
        bleClient?.startScan()
        _uiText.value = "Escaneando lentes BLE..."
    }

    fun startBleStreaming() {
        val ble = bleClient ?: return
        stopAll()

        _uiText.value = "🔗 Conectando para Lentes..."
        _connectionStatus.value = "Conectando..."

        viewModelScope.launch {
            val connected = sttRepo.connect()

            if (!connected) {
                _uiText.value = "❌ No se pudo conectar"
                return@launch
            }

            _uiText.value = "🔗 Usando Lentes..."
            _connectionStatus.value = "BLE Activo"
            listenToWebSocketResponses()

            audioJob = viewModelScope.launch {
                ble.audioFlow.collectLatest { chunk ->
                    sttRepo.sendChunk(chunk)
                }
            }
        }
    }
}