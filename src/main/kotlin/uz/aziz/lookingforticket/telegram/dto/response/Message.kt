package uz.aziz.lookingforticket.telegram.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class Message(
    @JsonProperty("message_id")
    val messageId: Long,
    val from: User? = null,
    val chat: Chat,
    val date: Long,
    val text: String? = null
)

