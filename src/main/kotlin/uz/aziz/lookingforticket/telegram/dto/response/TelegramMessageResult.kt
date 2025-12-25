package uz.aziz.lookingforticket.telegram.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TelegramMessageResult(
    @JsonProperty("message_id")
    val messageId: Long? = null
)
