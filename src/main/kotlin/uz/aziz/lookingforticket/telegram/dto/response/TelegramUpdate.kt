package uz.aziz.lookingforticket.telegram.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TelegramUpdate(
    @JsonProperty("update_id")
    val updateId: Long,
    val message: Message? = null,
    @JsonProperty("callback_query")
    val callbackQuery: CallbackQuery? = null
)

