package uz.aziz.lookingforticket.telegram.dto.response

data class GetUpdatesResponse(
    val ok: Boolean,
    val result: List<TelegramUpdate>? = null
)

