package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TrainResponse(
    @JsonProperty("number")
    val number: String?,
    
    @JsonProperty("number2")
    val number2: String?,
    
    @JsonProperty("brand")
    val brand: String?,
    
    @JsonProperty("type")
    val type: String?,
    
    @JsonProperty("route")
    val route: RouteResponse?,
    
    @JsonProperty("places")
    val places: PlacesResponse?,
    
    @JsonProperty("departure")
    val departure: TimeResponse?,
    
    @JsonProperty("arrival")
    val arrival: TimeResponse?,
    
    @JsonProperty("timeInWay")
    val timeInWay: String?
)

