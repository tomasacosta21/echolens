package ar.edu.unrn.echolens.ai

import ar.edu.unrn.echolens.ai.ws.AudioSpec
import ar.edu.unrn.echolens.ai.ws.ResultMsg
import ar.edu.unrn.echolens.ai.ws.SttWebSocketClient
import kotlinx.coroutines.flow.Flow

class SttWsRepo(
    private val client: SttWebSocketClient
): SttRepository {

    override fun connect() = client.connect()
    override fun close() = client.close()

    override fun start(sessionId: String, spec: AudioSpec, lang: String, translateTo: String?) =
        client.startSession(sessionId, spec, lang, translateTo)

    override fun sendChunk(sessionId: String, seq: Int, bytes: ByteArray) =
        client.sendAudio(sessionId, seq, bytes)

    override fun end(sessionId: String) = client.endSession(sessionId)

    override fun results(): Flow<ResultMsg> = client.resultsFlow()
    override fun errors(): Flow<String> = client.errorsFlow()
}
