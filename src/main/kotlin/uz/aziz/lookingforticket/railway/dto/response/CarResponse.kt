package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class CarResponse(
    @JsonProperty("type")
    val type: String?,
    
    @JsonProperty("typeShow")
    val typeShow: String?,
    
    @JsonProperty("freeSeats")
    val freeSeats: String?,
    
    @JsonProperty("indexType")
    val indexType: String?,
    
    @JsonProperty("seats")
    val seats: SeatsResponse?,
    
    @JsonProperty("tariffs")
    val tariffs: TariffsResponse?
)

