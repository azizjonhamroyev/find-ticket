package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class PlacesResponse(
    @JsonProperty("cars")
    val cars: List<CarResponse>?
)

