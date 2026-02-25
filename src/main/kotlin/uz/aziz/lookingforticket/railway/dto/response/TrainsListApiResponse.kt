package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

/** Top-level response from POST /api/v3/handbook/trains/list */
data class TrainsListApiResponse(
    @JsonProperty("data")
    val data: TrainsListDataResponse?,

    @JsonProperty("error")
    val error: Any? = null
)

data class TrainsListDataResponse(
    @JsonProperty("directions")
    val directions: TrainsListDirectionsResponse?
)

data class TrainsListDirectionsResponse(
    @JsonProperty("forward")
    val forward: ForwardTrainsResponse?
)

data class ForwardTrainsResponse(
    @JsonProperty("trains")
    val trains: List<TrainV3Response>?
)
