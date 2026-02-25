package uz.aziz.lookingforticket.telegram.dto.response

data class TelegramApiResponse(
    val ok: Boolean,
    val description: String? = null,
    val result: TelegramMessageResult? = null
)

