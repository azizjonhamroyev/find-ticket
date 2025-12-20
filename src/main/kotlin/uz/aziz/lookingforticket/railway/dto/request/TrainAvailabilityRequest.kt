package uz.aziz.lookingforticket.railway.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TrainAvailabilityRequest(
    @JsonProperty("stationFrom")
    val stationFrom: String,
    
    @JsonProperty("stationTo")
    val stationTo: String,
    
    @JsonProperty("direction")
    val direction: List<DirectionRequest>,
    
    @JsonProperty("detailNumPlaces")
    val detailNumPlaces: Int = 1,
    
    @JsonProperty("showWithoutPlaces")
    val showWithoutPlaces: Int = 0
) {
    companion object {
        fun create(
            stationFrom: String,
            stationTo: String,
            depDate: LocalDate
        ): TrainAvailabilityRequest {
            return TrainAvailabilityRequest(
                stationFrom = stationFrom,
                stationTo = stationTo,
                direction = listOf(
                    DirectionRequest(
                        depDate = depDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                        fullday = true,
                        type = "Forward"
                    )
                ),
                detailNumPlaces = 1,
                showWithoutPlaces = 0
            )
        }
    }
}

