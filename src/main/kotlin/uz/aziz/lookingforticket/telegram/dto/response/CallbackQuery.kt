package uz.aziz.lookingforticket.telegram.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class CallbackQuery(
    @JsonProperty("id")
    val id: String,
    
    val from: User,
    
    val message: Message? = null,
    
    @JsonProperty("data")
    val data: String? = null
)




