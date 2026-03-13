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
