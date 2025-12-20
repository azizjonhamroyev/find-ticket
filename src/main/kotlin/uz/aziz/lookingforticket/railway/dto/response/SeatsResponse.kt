package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class SeatsResponse(
    @JsonProperty("seatsUndef")
    val seatsUndef: String?,
    
    @JsonProperty("seatsDn")
    val seatsDn: String?,
    
    @JsonProperty("seatsUp")
    val seatsUp: String?,
    
    @JsonProperty("seatsLateralDn")
    val seatsLateralDn: String?,
    
    @JsonProperty("seatsLateralUp")
    val seatsLateralUp: String?,
    
    @JsonProperty("freeComp")
    val freeComp: String?
)

