package uz.aziz.lookingforticket.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "railway.uz")
data class RailwayUzProperties(
    var baseUrl: String = "https://e-ticket.railway.uz",
    var xsrfToken: String = "",
    var cookie: String = ""
)

