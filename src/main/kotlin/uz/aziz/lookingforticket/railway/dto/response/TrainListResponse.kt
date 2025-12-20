package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TrainListResponse(
    @JsonProperty("date")
    val date: String?,
    
    @JsonProperty("train")
    val train: List<TrainResponse>?
)

