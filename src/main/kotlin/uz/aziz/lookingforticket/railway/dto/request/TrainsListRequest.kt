package uz.aziz.lookingforticket.railway.dto.request

import com.fasterxml.jackson.annotation.JsonProperty

data class TrainsListRequest(
    @JsonProperty("directions")
    val directions: DirectionsRequest
)

data class DirectionsRequest(
    @JsonProperty("forward")
    val forward: ForwardDirectionRequest
)
