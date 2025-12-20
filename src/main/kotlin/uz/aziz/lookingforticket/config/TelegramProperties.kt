package uz.aziz.lookingforticket.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "telegram")
data class TelegramProperties(
    var botToken: String = "",
    var botUsername: String = ""
)

