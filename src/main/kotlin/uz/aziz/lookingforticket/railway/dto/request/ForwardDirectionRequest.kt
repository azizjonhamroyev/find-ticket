package uz.aziz.lookingforticket.railway.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ForwardDirectionRequest(
    @JsonProperty("date")
    val date: String,

    @JsonProperty("depStationCode")
    val depStationCode: String,

    @JsonProperty("arvStationCode")
    val arvStationCode: String
) {
    companion object {
        fun create(depStationCode: String, arvStationCode: String, depDate: LocalDate): ForwardDirectionRequest {
            return ForwardDirectionRequest(
                date = depDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                depStationCode = depStationCode,
                arvStationCode = arvStationCode
            )
        }
    }
}
