package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class RouteNamesResponse(
    @JsonProperty("depStationName")
    val depStationName: String?,

    @JsonProperty("arvStationName")
    val arvStationName: String?
)
