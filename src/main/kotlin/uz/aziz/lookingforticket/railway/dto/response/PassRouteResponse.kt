package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class PassRouteResponse(
    @JsonProperty("from")
    val from: String?,
    
    @JsonProperty("codeFrom")
    val codeFrom: String?,
    
    @JsonProperty("to")
    val to: String?,
    
    @JsonProperty("codeTo")
    val codeTo: String?
)

