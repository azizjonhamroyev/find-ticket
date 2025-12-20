package uz.aziz.lookingforticket.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "scheduler")
data class SchedulerProperties(
    var checkIntervalMinutes: Long = 1
)

