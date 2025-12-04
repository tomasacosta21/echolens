package ar.edu.unrn.echolens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unrn.echolens.ai.SttRepository
import ar.edu.unrn.echolens.ai.SttWsRepo
import ar.edu.unrn.echolens.ai.ws.SttWebSocketClient
import ar.edu.unrn.echolens.ble.BleClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SttBleViewModel : ViewModel() {

    private var bleClient: BleClient? = null

    private val sttRepo: SttRepository =
        SttWsRepo(
            SttWebSocketClient(
                "wss://supersensitive-eloisa-exuberant.ngrok-free.dev/ws/audio"
            )
        )

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

    /** Inicia pipeline BLE -> WS -> BLE */
    fun startStreaming() {
        val ble = bleClient ?: return

        sttRepo.connect()

        // 1) BLE -> WS (manda bytes PCM)
        bleJob = viewModelScope.launch {
            ble.audioFlow.collectLatest { chunk ->
                sttRepo.sendChunk(chunk)
            }
        }

        // 2) WS -> BLE (manda texto a lentes)
        wsJob = viewModelScope.launch {
            sttRepo.results().collectLatest { msg ->
                val textToSend = msg.text
                if (!textToSend.isNullOrBlank()) {
                    ble.sendTextToEsp(textToSend)
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
        bleJob?.cancel()
        wsJob?.cancel()
        sttRepo.close()
    }
}
