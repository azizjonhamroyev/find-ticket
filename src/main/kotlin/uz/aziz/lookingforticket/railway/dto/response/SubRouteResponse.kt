package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class SubRouteResponse(
    @JsonProperty("depStationName")
    val depStationName: String?,

    @JsonProperty("depStationCode")
    val depStationCode: String?,

    @JsonProperty("arvStationName")
    val arvStationName: String?,

    @JsonProperty("arvStationCode")
    val arvStationCode: String?
)
