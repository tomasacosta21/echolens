package ar.edu.unrn.echolens.ai

import ar.edu.unrn.echolens.ai.ws.SttWebSocketClient
import ar.edu.unrn.echolens.ai.ws.TranscriptionMsg
import kotlinx.coroutines.flow.Flow

class SttWsRepo(
    private val client: SttWebSocketClient
) : SttRepository {

    override fun connect() = client.connect()
    override fun close() = client.close()

    override fun sendChunk(pcmBytes: ByteArray) =
        client.sendPcmChunk(pcmBytes)

    override fun results(): Flow<TranscriptionMsg> = client.resultsFlow()
    override fun errors(): Flow<String> = client.errorsFlow()
}
