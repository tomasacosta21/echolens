package ar.edu.unrn.echolens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.edu.unrn.echolens.ai.SttWsRepo
import ar.edu.unrn.echolens.ai.ws.AudioSpec
import ar.edu.unrn.echolens.ai.ws.SttWebSocketClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SttServiceIntegrationTest {

    @Test
    fun `send recorded audio to service and receive transcript`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val audioBytes = ctx.assets.open("test_audio.wav").readBytes()

        val repo = SttWsRepo(
            SttWebSocketClient("wss://TU_SERVICIO/ws/stt")
        )

        val sessionId = UUID.randomUUID().toString()
        val spec = AudioSpec(
            encoding = "wav",
            sampleRate = 16000,
            channels = 1,
            mimeType = "audio/wav"
        )

        repo.connect()
        repo.start(sessionId, spec, lang = "es", translateTo = null)

        // enviamos el audio entero como 1 chunk (simple para test)
        repo.sendChunk(sessionId, seq = 0, bytes = audioBytes)
        repo.end(sessionId)

        val finalResult = repo.results()
            .filter { it.isFinal }
            .first()

        repo.close()

        val text = finalResult.transcript ?: ""
        assertTrue("Transcript should not be empty", text.isNotBlank())
    }
}
