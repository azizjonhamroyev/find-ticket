package uz.aziz.lookingforticket.railway.dto.request

import com.fasterxml.jackson.annotation.JsonProperty

data class DirectionsRequest(
    @JsonProperty("forward")
    val forward: ForwardDirectionRequest
)
