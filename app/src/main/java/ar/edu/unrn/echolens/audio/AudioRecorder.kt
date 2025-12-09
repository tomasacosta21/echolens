package ar.edu.unrn.echolens.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

class AudioRecorder {

    private val TAG = "AudioRecorder"
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // Buffer interno un poco más grande para evitar cortes
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val bufferSize = minBufferSize * 4

    @SuppressLint("MissingPermission")
    fun startRecording(): Flow<ByteArray> = flow {
        Log.d(TAG, "Iniciando AudioRecord...")

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Hardware de audio no soportado en este dispositivo.")
        }

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Fallo al inicializar AudioRecord (Estado no inicializado).")
        }

        try {
            recorder.startRecording()
            Log.d(TAG, "Grabación comenzada.")

            val buffer = ByteArray(4096) // Chunk de envío

            while (coroutineContext.isActive) {
                // read es bloqueante, por eso usamos flowOn(Dispatchers.IO) abajo
                val readResult = recorder.read(buffer, 0, buffer.size)

                if (readResult < 0) {
                    Log.e(TAG, "Error leyendo audio: $readResult")
                    break
                }

                if (readResult > 0) {
                    emit(buffer.copyOfRange(0, readResult))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción grabando: ${e.message}")
            throw e
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
                recorder.release()
                Log.d(TAG, "AudioRecord liberado.")
            } catch (e: Exception) {
                Log.e(TAG, "Error al liberar recorder: ${e.message}")
            }
        }
    }.flowOn(Dispatchers.IO) // <--- ESTO ES CRUCIAL PARA EVITAR CRASHES
}