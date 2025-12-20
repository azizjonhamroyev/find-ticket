package uz.aziz.lookingforticket.railway.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class TariffResponse(
    @JsonProperty("tariff")
    val tariff: String?,
    
    @JsonProperty("tariffService")
    val tariffService: String?,
    
    @JsonProperty("comissionFee")
    val comissionFee: String?
)

