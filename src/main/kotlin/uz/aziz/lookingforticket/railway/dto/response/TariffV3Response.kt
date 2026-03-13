package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TariffV3Response(
    @JsonProperty("classServiceType")
    val classServiceType: String?,

    @JsonProperty("freeSeats")
    val freeSeats: Int?,

    @JsonProperty("tariff")
    val tariff: Int?
)
