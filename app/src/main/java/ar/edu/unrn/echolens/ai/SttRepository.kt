package ar.edu.unrn.echolens.ai

import ar.edu.unrn.echolens.ai.ws.TranscriptionMsg
import kotlinx.coroutines.flow.Flow

interface SttRepository {
    fun connect()
    fun close()
    fun sendChunk(pcmBytes: ByteArray)
    fun results(): Flow<TranscriptionMsg>
    fun errors(): Flow<String>
}
