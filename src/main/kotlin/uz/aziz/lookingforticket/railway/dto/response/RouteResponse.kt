package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class RouteResponse(
    @JsonProperty("station")
    val station: List<String>?
)

