package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TariffsResponse(
    @JsonProperty("tariff")
    val tariff: List<TariffResponse>?
)

