package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class CarV3Response(
    @JsonProperty("type")
    val type: String?,

    @JsonProperty("freeSeats")
    val freeSeats: Int?,

    @JsonProperty("tariffs")
    val tariffs: List<TariffV3Response>?,

    @JsonProperty("seatDetail")
    val seatDetail: SeatDetailResponse? = null
)

data class TariffV3Response(
    @JsonProperty("classServiceType")
    val classServiceType: String?,

    @JsonProperty("freeSeats")
    val freeSeats: Int?,

    @JsonProperty("tariff")
    val tariff: Int?
)

data class SeatDetailResponse(
    @JsonProperty("undef")
    val undef: Int? = null,

    @JsonProperty("lateralDn")
    val lateralDn: Int? = null,

    @JsonProperty("lateralUp")
    val lateralUp: Int? = null,

    @JsonProperty("freeComp")
    val freeComp: Int? = null,

    @JsonProperty("down")
    val down: Int? = null,

    @JsonProperty("up")
    val up: Int? = null
)
