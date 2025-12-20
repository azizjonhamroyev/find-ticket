package uz.aziz.lookingforticket

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class LookingForTicketApplication

fun main(args: Array<String>) {
    runApplication<LookingForTicketApplication>(*args)
}
