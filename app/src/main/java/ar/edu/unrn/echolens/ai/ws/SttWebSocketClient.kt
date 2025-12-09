package ar.edu.unrn.echolens.ai.ws

import android.util.Log
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okio.ByteString.Companion.toByteString

class SttWebSocketClient(
    private val wsUrl: String,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private val TAG = "SttWebSocketClient"
    private val moshi = Moshi.Builder().build()
    private val transcriptionAdapter = moshi.adapter(TranscriptionMsg::class.java)

    // Usamos SharedFlow en vez de Channel para flows
    private val _results = MutableSharedFlow<TranscriptionMsg>(replay = 0, extraBufferCapacity = 64)
    private val _errors = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 16)

    // Canal para audio saliente
    private var outgoingBinary: Channel<ByteArray>? = null
    private var ws: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var outgoingJob: Job? = null

    // 🔥 NUEVO: CompletableDeferred para saber cuándo conectó
    private var connectionReady: CompletableDeferred<Boolean>? = null

    fun resultsFlow(): Flow<TranscriptionMsg> = _results.asSharedFlow()
    fun errorsFlow(): Flow<String> = _errors.asSharedFlow()

    suspend fun connect(): Boolean {
        Log.d(TAG, "Conectando a: $wsUrl")

        // 1. Resetear recursos
        resetResources()

        // 2. Crear deferred para esperar onOpen
        connectionReady = CompletableDeferred()

        // 3. Iniciar conexión real
        val req = Request.Builder().url(wsUrl).build()
        ws = okHttpClient.newWebSocket(req, listener)

        // 4. Iniciar trabajo de envío
        outgoingJob?.cancel()
        outgoingJob = scope?.launch {
            val channel = outgoingBinary ?: return@launch
            for (chunk in channel) {
                try {
                    ws?.send(chunk.toByteString())
                } catch (e: Exception) {
                    Log.e(TAG, "Error enviando bytes: ${e.message}")
                }
            }
        }

        // 5. 🔥 ESPERAR hasta que onOpen se ejecute (máx 10s)
        return try {
            withTimeout(10_000) {
                connectionReady?.await() ?: false
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timeout esperando conexión")
            _errors.tryEmit("Timeout: No se pudo conectar en 10s")
            false
        }
    }

    private fun resetResources() {
        if (scope == null || !scope!!.isActive) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }

        if (outgoingBinary == null || outgoingBinary!!.isClosedForSend) {
            outgoingBinary = Channel(Channel.BUFFERED)
        }
    }

    fun close() {
        Log.d(TAG, "Cerrando conexión WebSocket")
        outgoingJob?.cancel()

        ws?.close(1000, "bye")
        ws = null

        outgoingBinary?.close()

        scope?.cancel()
        scope = null
        outgoingBinary = null

        connectionReady?.cancel()
        connectionReady = null
    }

    fun sendPcmChunk(pcmBytes: ByteArray) {
        val channel = outgoingBinary
        if (channel != null && !channel.isClosedForSend) {
            channel.trySend(pcmBytes)
        } else {
            Log.w(TAG, "⚠️ Intentando enviar pero canal cerrado")
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "✅ WebSocket Abierto y listo")
            // 🔥 AVISAR que ya conectó
            connectionReady?.complete(true)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope?.launch {
                runCatching { transcriptionAdapter.fromJson(text) }
                    .onSuccess { msg ->
                        if (msg != null) {
                            Log.d(TAG, "📩 Mensaje recibido: type=${msg.type}, text=${msg.text}")
                            _results.emit(msg)
                        }
                    }
                    .onFailure { e ->
                        Log.e(TAG, "Error parseando JSON: $text", e)
                    }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "❌ Fallo WebSocket: ${t.message}")
            scope?.launch {
                _errors.emit(t.message ?: "Error desconocido")
            }
            // 🔥 Si falla antes de conectar, marcar como fallido
            connectionReady?.complete(false)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Cerrando WS: $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WS cerrado: $reason")
        }
    }
}