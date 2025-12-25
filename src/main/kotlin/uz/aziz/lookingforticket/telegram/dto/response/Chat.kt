package uz.aziz.lookingforticket.telegram.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class Chat(
    val id: Long,
    val type: String? = null,
    val username: String? = null,
    @JsonProperty("first_name")
    val firstName: String? = null,
    @JsonProperty("last_name")
    val lastName: String? = null

)

