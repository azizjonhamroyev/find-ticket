package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class ExpressResponse(
    @JsonProperty("hasError")
    val hasError: Boolean,
    
    @JsonProperty("type")
    val type: String?,
    
    @JsonProperty("direction")
    val direction: List<DirectionResponse>?,
    
    @JsonProperty("showWithoutPlaces")
    val showWithoutPlaces: Any? = null,
    
    @JsonProperty("reqExpressZK")
    val reqExpressZK: String? = null,
    
    @JsonProperty("reqLocalSend")
    val reqLocalSend: String? = null,
    
    @JsonProperty("reqLocalRecv")
    val reqLocalRecv: String? = null,
    
    @JsonProperty("reqAddress")
    val reqAddress: String? = null,
    
    @JsonProperty("reqExpressDateTime")
    val reqExpressDateTime: String? = null,
    
    @JsonProperty("content")
    val content: String? = null,
    
    @JsonProperty("code")
    val code: Any? = null
)

