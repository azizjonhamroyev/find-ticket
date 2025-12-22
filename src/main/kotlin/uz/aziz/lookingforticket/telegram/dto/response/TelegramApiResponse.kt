package uz.aziz.lookingforticket.telegram.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TelegramApiResponse(
    val ok: Boolean,
    val description: String? = null,
    val result: TelegramMessageResult? = null
)

data class TelegramMessageResult(
    @JsonProperty("message_id")
    val messageId: Long? = null
)

