package uz.aziz.lookingforticket.telegram.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class User(
    val id: Long,
    @JsonProperty("is_bot")
    val isBot: Boolean = false,
    @JsonProperty("first_name")
    val firstName: String? = null,
    @JsonProperty("last_name")
    val lastName: String? = null,
    val username: String? = null
)

