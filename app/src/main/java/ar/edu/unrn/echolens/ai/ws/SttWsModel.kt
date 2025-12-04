package ar.edu.unrn.echolens.ai.ws

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TranscriptionMsg(
    val type: String,
    val text: String? = null,
    val final: Boolean = false,
    val timestamp: String? = null,
    val speaker: String? = null
)

@JsonClass(generateAdapter = true)
data class PongMsg(
    val type: String
)
