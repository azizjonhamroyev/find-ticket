package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class DirectionResponse(
    @JsonProperty("type")
    val type: String?,
    
    @JsonProperty("trains")
    val trains: List<TrainListResponse>?,
    
    @JsonProperty("passRoute")
    val passRoute: PassRouteResponse?,
    
    @JsonProperty("notAllTrains")
    val notAllTrains: Any? = null
)

