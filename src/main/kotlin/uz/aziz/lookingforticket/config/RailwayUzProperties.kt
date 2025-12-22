package uz.aziz.lookingforticket.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "railway.uz")
data class RailwayUzProperties(
    var baseUrl: String = "https://e-ticket.railway.uz",
    var xsrfToken: String = "",
    var cookie: String = "",
    var delayBetweenRequestsMs: Long = 2000, // 2 seconds delay between requests
    var maxRetries: Int = 3, // Maximum retry attempts for 429 errors
    var initialRetryDelayMs: Long = 5000, // Initial delay for retry (5 seconds)
    var maxRetryDelayMs: Long = 60000 // Maximum delay for retry (60 seconds)
)

