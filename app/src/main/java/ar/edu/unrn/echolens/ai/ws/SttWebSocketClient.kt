package ar.edu.unrn.echolens.ai.ws

import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString

class SttWebSocketClient(
    private val wsUrl: String,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private val moshi = Moshi.Builder().build()
    private val transcriptionAdapter = moshi.adapter(TranscriptionMsg::class.java)

    private val outgoingBinary = Channel<ByteArray>(Channel.BUFFERED)
    private val incoming = Channel<TranscriptionMsg>(Channel.BUFFERED)
    private val errors = Channel<String>(Channel.BUFFERED)

    private var ws: WebSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var outgoingJob: Job? = null

    fun resultsFlow(): Flow<TranscriptionMsg> = incoming.receiveAsFlow()
    fun errorsFlow(): Flow<String> = errors.receiveAsFlow()

    fun connect() {
        val req = Request.Builder().url(wsUrl).build()
        ws = okHttpClient.newWebSocket(req, listener)

        outgoingJob?.cancel()
        outgoingJob = scope.launch {
            for (chunk in outgoingBinary) {
                ws?.send(chunk.toByteString())
            }
        }
    }

    fun close() {
        outgoingJob?.cancel()
        outgoingBinary.close()

        ws?.close(1000, "bye")
        ws?.cancel()
        ws = null

        scope.cancel()
    }

    /** Envía PCM16 raw en binario */
    fun sendPcmChunk(pcmBytes: ByteArray) {
        outgoingBinary.trySend(pcmBytes)
    }

    /** Opcional: ping si querés mantener viva la conexión */
    fun sendPing() {
        ws?.send("""{"command":"ping"}""")
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching { transcriptionAdapter.fromJson(text) }
                .getOrNull()
                ?.let { msg ->
                    if (msg.type == "transcription") incoming.trySend(msg)
                }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            errors.trySend(t.message ?: "WebSocket failure")
        }
    }
}
