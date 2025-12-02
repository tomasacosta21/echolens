package ar.edu.unrn.echolens.ai

import ar.edu.unrn.echolens.ai.ws.AudioSpec
import ar.edu.unrn.echolens.ai.ws.ResultMsg
import kotlinx.coroutines.flow.Flow

interface SttRepository {
    fun connect()
    fun close()

    fun start(sessionId: String, spec: AudioSpec, lang: String, translateTo: String? = null)
    fun sendChunk(sessionId: String, seq: Int, bytes: ByteArray)
    fun end(sessionId: String)

    fun results(): Flow<ResultMsg>
    fun errors(): Flow<String>
}
