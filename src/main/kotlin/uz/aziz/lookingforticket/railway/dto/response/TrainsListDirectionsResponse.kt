package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TrainsListDirectionsResponse(
    @JsonProperty("forward")
    val forward: ForwardTrainsResponse?
)
