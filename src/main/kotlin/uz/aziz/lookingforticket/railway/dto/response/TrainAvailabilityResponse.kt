package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TrainAvailabilityResponse(
    @JsonProperty("express")
    val express: ExpressResponse?,
    
    @JsonProperty("discount")
    val discount: Any? = null
)

