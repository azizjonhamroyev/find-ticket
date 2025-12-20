package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TimeResponse(
    @JsonProperty("time")
    val time: String?,
    
    @JsonProperty("localTime")
    val localTime: String?,
    
    @JsonProperty("date")
    val date: String?,
    
    @JsonProperty("localDate")
    val localDate: String?,
    
    @JsonProperty("stop")
    val stop: String?
)

