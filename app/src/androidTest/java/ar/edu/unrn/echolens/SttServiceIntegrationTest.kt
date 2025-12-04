package ar.edu.unrn.echolens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SttServiceIntegrationTest {

    private val url = "wss://supersensitive-eloisa-exuberant.ngrok-free.dev/ws/audio"

    private fun buildClient(): OkHttpClient =
        OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

    @Test
    fun connects_and_receives_pong() = runBlocking {
        val client = buildClient()

        val connected = CompletableDeferred<Boolean>()
        val gotPong = CompletableDeferred<Boolean>()

        val request = Request.Builder().url(url).build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!connected.isCompleted) connected.complete(true)
                // tu backend espera {"command":"ping"} como texto
                webSocket.send("""{"command":"ping"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"type\":\"pong\"") && !gotPong.isCompleted) {
                    gotPong.complete(true)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!connected.isCompleted) connected.complete(false)
                if (!gotPong.isCompleted) gotPong.complete(false)
            }
        })

        val okConnect = connected.await()
        val okPong = withTimeout(10_000) { gotPong.await() }

        ws.close(1000, "bye")
        client.dispatcher.executorService.shutdown()

        assertTrue("WebSocket should connect", okConnect)
        assertTrue("Should receive pong after ping", okPong)
    }

    @Test
    fun sendPcm_and_receives_transcription() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
       // val pcmBytes = ctx.assets.open("test_audio.pcm").readBytes()
        val pcmBytes = ctx.assets.open("test_audio.wav").readBytes()
        assertTrue("Asset test_audio.pcm is empty or missing", pcmBytes.isNotEmpty())

        // Validación mínima de 4s a 16kHz PCM16 mono (igual que backend)
        val samples = pcmBytes.size / 2
        val seconds = samples / 16000.0
        require(seconds >= 4.0) {
            "El backend requiere MIN_AUDIO_LENGTH=4s. Tu archivo tiene ~${"%.2f".format(seconds)}s"
        }

        val client = buildClient()

        val connected = CompletableDeferred<Boolean>()
        val transcript = CompletableDeferred<String?>()

        val request = Request.Builder().url(url).build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!connected.isCompleted) connected.complete(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // backend manda JSON {"type":"transcription","text":"..."}
                if (text.contains("\"type\":\"transcription\"")) {
                    val match = Regex("\"text\"\\s*:\\s*\"(.*?)\"").find(text)
                    val t = match?.groupValues?.get(1)
                    if (!t.isNullOrBlank() && !transcript.isCompleted) {
                        transcript.complete(t)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!connected.isCompleted) connected.complete(false)
                if (!transcript.isCompleted) transcript.complete(null)
            }
        })

        // 1) esperar conexión real
        assertTrue("WebSocket should connect", connected.await())

        // 2) enviar frames tipo UI web: 4096 samples => 8192 bytes/frame
        val frameSizeBytes = 4096 * 2
        var offset = 0
        while (offset < pcmBytes.size) {
            val end = minOf(offset + frameSizeBytes, pcmBytes.size)
            val frame = pcmBytes.copyOfRange(offset, end)

            ws.send(frame.toByteString())

            offset = end
            delay(256) // emula tiempo real (~256ms por frame)
        }

        // 3) esperar transcripción
        val text = withTimeout(60_000) { transcript.await() }

        ws.close(1000, "bye")
        client.dispatcher.executorService.shutdown()

        assertTrue("Transcript should not be empty", !text.isNullOrBlank())
    }
}
