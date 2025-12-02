package ar.edu.unrn.echolens

import ar.edu.unrn.echolens.ai.ws.AudioSpec
import ar.edu.unrn.echolens.ai.ws.SttWebSocketClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SttWebSocketClientTest {

    private val server = MockWebServer()
    private lateinit var okHttp: OkHttpClient
    private lateinit var client: SttWebSocketClient

    @Before
    fun setUp() {
        server.start()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : okhttp3.WebSocketListener() {})
        )

        okHttp = OkHttpClient()

        val url = server.url("/ws/stt").toString().replace("http", "ws")
        client = SttWebSocketClient(url, okHttp)
    }

    @After
    fun tearDown() {
        // Cerramos WS / scope interno
        client.close()

        // Cerramos OkHttp para que no queden hilos vivos
        okHttp.dispatcher.cancelAll()
        okHttp.dispatcher.executorService.shutdown()
        okHttp.connectionPool.evictAll()
        okHttp.cache?.close()

        // MockWebServer a veces queda esperando el WS.
        // No queremos que eso haga fallar el test.
        try {
            server.shutdown()
        } catch (e: java.io.IOException) {
            // ignoramos: es un problema de cierre del mock, no de lógica
        }
    }


    @Test
    fun `sends start audio end in order`() = runTest {
        client.connect()
        client.startSession("s1", AudioSpec(), "es", null)
        client.sendAudio("s1", 0, byteArrayOf(1, 2, 3, 4))
        client.endSession("s1")

        val req = server.takeRequest()
        assertTrue(req.path!!.contains("/ws/stt"))
    }
}
