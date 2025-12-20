package uz.aziz.lookingforticket.railway.dto.request

import com.fasterxml.jackson.annotation.JsonProperty

data class DirectionRequest(
    @JsonProperty("depDate")
    val depDate: String,
    
    @JsonProperty("fullday")
    val fullday: Boolean = true,
    
    @JsonProperty("type")
    val type: String = "Forward"
)

