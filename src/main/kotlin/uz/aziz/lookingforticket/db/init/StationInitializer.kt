package uz.aziz.lookingforticket.db.init

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uz.aziz.lookingforticket.db.entity.StationEntity
import uz.aziz.lookingforticket.db.repo.StationRepository

@Component
class StationInitializer(
    private val stationRepository: StationRepository
) {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @PostConstruct
    fun initializeStations() {
        if (stationRepository.count() == 0L) {
            logger.info("Initializing stations...")
            
            val stations = listOf(
                StationEntity(id = "2900000", name = "Tashkent"),
                StationEntity(id = "2900800", name = "Bukhara")
            )
            
            stationRepository.saveAll(stations)
            logger.info("Initialized ${stations.size} stations")
        } else {
            logger.debug("Stations already initialized")
        }
    }
}

