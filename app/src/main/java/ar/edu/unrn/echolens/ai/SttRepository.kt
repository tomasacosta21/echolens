package ar.edu.unrn.echolens.ai

import ar.edu.unrn.echolens.ai.ws.TranscriptionMsg
import kotlinx.coroutines.flow.Flow

interface SttRepository {
    /**
     * Conecta al servidor STT.
     * @return true si la conexión fue exitosa, false si falló
     */
    suspend fun connect(): Boolean

    /**
     * Envía un chunk de audio PCM16 al servidor
     */
    fun sendChunk(pcmBytes: ByteArray)

    /**
     * Flow de transcripciones recibidas del servidor
     */
    fun results(): Flow<TranscriptionMsg>

    /**
     * Flow de errores de conexión
     */
    fun errors(): Flow<String>

    /**
     * Cierra la conexión
     */
    fun close()
}