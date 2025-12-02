package ar.edu.unrn.echolens.ai.ws

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StartMsg(
    val type: String = "start",
    val sessionId: String,
    val audio: AudioSpec,
    val lang: String = "es",
    val translateTo: String? = null
)

@JsonClass(generateAdapter = true)
data class AudioSpec(
    val encoding: String = "pcm16le", // o opus, wav, etc.
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val mimeType: String = "audio/raw"
)

@JsonClass(generateAdapter = true)
data class AudioMsg(
    val type: String = "audio",
    val sessionId: String,
    val seq: Int,
    val dataB64: String
)

@JsonClass(generateAdapter = true)
data class EndMsg(
    val type: String = "end",
    val sessionId: String
)

@JsonClass(generateAdapter = true)
data class ResultMsg(
    val type: String = "result",
    val sessionId: String,
    val seq: Int? = null,
    val isFinal: Boolean = false,
    val transcript: String? = null,
    val translation: String? = null
)

@JsonClass(generateAdapter = true)
data class ErrorMsg(
    val type: String = "error",
    val sessionId: String? = null,
    val message: String
)
