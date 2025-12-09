package ar.edu.unrn.echolens.ai

import ar.edu.unrn.echolens.ai.ws.SttWebSocketClient
import ar.edu.unrn.echolens.ai.ws.TranscriptionMsg
import kotlinx.coroutines.flow.Flow

class SttWsRepo(
    private val wsClient: SttWebSocketClient
) : SttRepository {

    override suspend fun connect(): Boolean {
        return wsClient.connect()
    }

    override fun sendChunk(pcmBytes: ByteArray) {
        wsClient.sendPcmChunk(pcmBytes)
    }

    override fun results(): Flow<TranscriptionMsg> {
        return wsClient.resultsFlow()
    }

    override fun errors(): Flow<String> {
        return wsClient.errorsFlow()
    }

    override fun close() {
        wsClient.close()
    }
}