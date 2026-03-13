package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class ForwardTrainsResponse(
    @JsonProperty("trains")
    val trains: List<TrainV3Response>?
)
