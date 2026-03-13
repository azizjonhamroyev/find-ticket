package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TrainV3Response(
    @JsonProperty("type")
    val type: String?,

    @JsonProperty("number")
    val number: String?,

    @JsonProperty("departureDate")
    val departureDate: String?,

    @JsonProperty("timeOnWay")
    val timeOnWay: String?,

    @JsonProperty("originRoute")
    val originRoute: RouteNamesResponse?,

    @JsonProperty("arrivalDate")
    val arrivalDate: String?,

    @JsonProperty("brand")
    val brand: String?,

    @JsonProperty("cars")
    val cars: List<CarV3Response>?,

    @JsonProperty("subRoute")
    val subRoute: SubRouteResponse?,

    @JsonProperty("trainId")
    val trainId: Any? = null,

    @JsonProperty("comment")
    val comment: String? = null
)
