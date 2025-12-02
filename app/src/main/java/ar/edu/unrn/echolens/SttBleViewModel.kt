package ar.edu.unrn.echolens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unrn.echolens.ai.SttRepository
import ar.edu.unrn.echolens.ai.SttWsRepo
import ar.edu.unrn.echolens.ai.ws.AudioSpec
import ar.edu.unrn.echolens.ai.ws.SttWebSocketClient
import ar.edu.unrn.echolens.ble.BleClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class SttBleViewModel : ViewModel() {

    private var bleClient: BleClient? = null

    // Endpoint WS del servicio (poné el host real cuando esté)
    private val sttRepo: SttRepository =
        SttWsRepo(SttWebSocketClient(wsUrl = "wss://TU_SERVICIO/ws/stt"))

    private var sessionId: String? = null
    private var seq: Int = 0
    private var bleJob: Job? = null
    private var wsJob: Job? = null

    fun initBle(ctx: Context) {
        if (bleClient == null) bleClient = BleClient(ctx)
        bleClient?.startScan()
    }

    fun disconnectBle() {
        bleJob?.cancel()
        wsJob?.cancel()
        bleClient?.disconnect()
        sttRepo.close()
    }

    /**
     * Inicia pipeline BLE -> WS -> BLE
     */
    fun startStreaming(
        lang: String = "es",
        translateTo: String? = null,
        spec: AudioSpec = AudioSpec()
    ) {
        val ble = bleClient ?: return

        sessionId = UUID.randomUUID().toString()
        seq = 0

        sttRepo.connect()
        sttRepo.start(sessionId!!, spec, lang, translateTo)

        // 1) BLE -> WS (chunks)
        bleJob = viewModelScope.launch {
            ble.audioFlow.collectLatest { chunk ->
                sttRepo.sendChunk(sessionId!!, seq++, chunk)
            }
        }

        // 2) WS -> BLE (resultados)
        wsJob = viewModelScope.launch {
            sttRepo.results().collectLatest { result ->
                val textToSend = result.translation ?: result.transcript
                if (!textToSend.isNullOrBlank()) {
                    ble.sendTextToEsp(textToSend)
                }

                if (result.isFinal) {
                    // opcional: cerrar sesión si backend marca final
                    stopStreaming()
                }
            }
        }

        // errores
        viewModelScope.launch {
            sttRepo.errors().collectLatest {
                // log / ui state
            }
        }
    }

    fun stopStreaming() {
        val id = sessionId ?: return
        sttRepo.end(id)
        sttRepo.close()
        bleJob?.cancel()
        wsJob?.cancel()
        sessionId = null
    }
}
