package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

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
