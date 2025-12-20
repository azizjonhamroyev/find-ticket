package uz.aziz.lookingforticket.telegram.dto.request

import com.fasterxml.jackson.annotation.JsonProperty

data class SendMessageRequest(
    @JsonProperty("chat_id")
    val chatId: Long,
    val text: String,
    @JsonProperty("parse_mode")
    val parseMode: String? = null
)

