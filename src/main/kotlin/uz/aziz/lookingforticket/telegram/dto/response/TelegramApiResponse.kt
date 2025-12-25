package uz.aziz.lookingforticket.telegram.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

data class TelegramApiResponse(
    val ok: Boolean,
    val description: String? = null,
    val result: JsonNode? = null
) {
    // Helper method to get messageId from result if it's a Message object
    fun getMessageId(): Long? {
        return result?.get("message_id")?.asLong()
    }
    
    // Helper method to check if result is a boolean (for answerCallbackQuery)
    fun isResultBoolean(): Boolean {
        return result?.isBoolean == true
    }
}

