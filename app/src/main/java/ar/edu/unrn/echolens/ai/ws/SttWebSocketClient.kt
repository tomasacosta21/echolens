package ar.edu.unrn.echolens.ai.ws

import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.*
import okio.ByteString.Companion.toByteString

class SttWebSocketClient(
    private val wsUrl: String,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {

    private val moshi = Moshi.Builder().build()
    private val startAdapter = moshi.adapter(StartMsg::class.java)
    private val audioAdapter = moshi.adapter(AudioMsg::class.java)
    private val endAdapter = moshi.adapter(EndMsg::class.java)
    private val resultAdapter = moshi.adapter(ResultMsg::class.java)
    private val errorAdapter = moshi.adapter(ErrorMsg::class.java)

    private val outgoing = Channel<String>(Channel.BUFFERED)
    private val incoming = Channel<ResultMsg>(Channel.BUFFERED)
    private val errors = Channel<String>(Channel.BUFFERED)

    private var ws: WebSocket? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var outgoingJob: Job? = null

    fun resultsFlow(): Flow<ResultMsg> = incoming.receiveAsFlow()
    fun errorsFlow(): Flow<String> = errors.receiveAsFlow()

    fun connect() {
        val req = Request.Builder().url(wsUrl).build()
        ws = okHttpClient.newWebSocket(req, listener)

        outgoingJob?.cancel()
        outgoingJob = scope.launch {
            for (msg in outgoing) {
                ws?.send(msg)
            }
        }
    }

    fun close() {
        outgoingJob?.cancel()
        outgoing.close()

        // Cerramos educadamente y luego cancelamos por si queda colgado
        ws?.close(1000, "bye")
        ws?.cancel()   // <-- fuerza cierre inmediato del socket

        ws = null
        scope.cancel()
    }


    fun startSession(sessionId: String, spec: AudioSpec, lang: String, translateTo: String?) {
        val json = startAdapter.toJson(
            StartMsg(sessionId = sessionId, audio = spec, lang = lang, translateTo = translateTo)
        )
        outgoing.trySend(json)
    }

    fun sendAudio(sessionId: String, seq: Int, audioBytes: ByteArray) {
        val b64 = audioBytes.toByteString().base64()
        val json = audioAdapter.toJson(
            AudioMsg(sessionId = sessionId, seq = seq, dataB64 = b64)
        )
        outgoing.trySend(json)
    }

    fun endSession(sessionId: String) {
        val json = endAdapter.toJson(EndMsg(sessionId = sessionId))
        outgoing.trySend(json)
    }

    private val listener = object : WebSocketListener() {

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching { resultAdapter.fromJson(text) }
                .getOrNull()
                ?.let { incoming.trySend(it) }
                ?: runCatching { errorAdapter.fromJson(text) }
                    .getOrNull()
                    ?.let { errors.trySend(it.message) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            errors.trySend(t.message ?: "WebSocket failure")
        }
    }
}
