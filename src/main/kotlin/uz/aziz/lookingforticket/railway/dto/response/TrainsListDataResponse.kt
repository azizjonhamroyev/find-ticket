package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TrainsListDataResponse(
    @JsonProperty("directions")
    val directions: TrainsListDirectionsResponse?
)
