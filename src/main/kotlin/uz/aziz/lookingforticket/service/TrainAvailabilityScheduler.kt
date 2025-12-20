package uz.aziz.lookingforticket.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import uz.aziz.lookingforticket.config.SchedulerProperties
import uz.aziz.lookingforticket.db.RequestBrandRepository
import uz.aziz.lookingforticket.db.RequestRepository
import uz.aziz.lookingforticket.railway.RailwayApiService
import java.time.Duration
import java.time.LocalDateTime

@Service
class TrainAvailabilityScheduler(
    private val requestRepository: RequestRepository,
    private val requestBrandRepository: RequestBrandRepository,
    private val railwayApiService: RailwayApiService,
    private val telegramNotificationService: TelegramNotificationService,
    private val schedulerProperties: SchedulerProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @Scheduled(cron = "0 * * * * ?")
    @Transactional
    fun checkTrainAvailability() {
        logger.debug("Starting scheduled train availability check")
        
        val activeRequests = requestRepository.findByIsActiveTrue()
        
        if (activeRequests.isEmpty()) {
            logger.debug("No active requests to check")
            return
        }
        
        logger.info("Checking ${activeRequests.size} active requests")
        
        activeRequests.forEach { request ->
            try {
                checkRequest(request)
            } catch (e: Exception) {
                logger.error("Error checking request ${request.id}: ${e.message}", e)
            }
        }
        
        logger.debug("Completed scheduled train availability check")
    }
    
    private fun checkRequest(request: uz.aziz.lookingforticket.db.RequestEntity) {
        logger.debug(
            "Checking request {}: {} -> {} from {} to {}",
            request.id,
            request.stationFrom.name,
            request.stationTo.name,
            request.fromDate,
            request.toDate
        )
        
        val now = LocalDateTime.now()
        requestRepository.updateLastCheckedAt(request.id, now)
        
        // Get selected brands for this request
        val requestBrands = requestBrandRepository.findByRequestId(request.id)
        val brandNames = if (requestBrands.isEmpty()) {
            null // No brand filter (ALL)
        } else {
            requestBrands.map { it.brand.name }
        }
        
        val trains = railwayApiService.getAvailableTrainsWithSeatsForDateRange(
            stationFrom = request.stationFrom.id,
            stationTo = request.stationTo.id,
            fromDate = request.fromDate,
            toDate = request.toDate,
            minSeats = request.minSeats,
            brandNames = brandNames
        ).block(Duration.ofSeconds(30))
        
        if (!trains.isNullOrEmpty()) {
            logger.info("Found ${trains.size} available trains for request ${request.id}")
            
            // Check current notification count before incrementing
            val currentCount = request.notificationCount
            
            // Increment notification count
            requestRepository.incrementNotificationCount(request.id)
            
            // Send notification to user
            telegramNotificationService.notifyUserAboutAvailableTrains(
                chatId = request.user.chatId,
                trains = trains,
                requestId = request.id
            )
            
            // Update last notified timestamp
            requestRepository.updateLastNotifiedAt(request.id, now)
            
            // Check if notification count reached 2 (was 1, now becomes 2), then ask user to deactivate
            // If user ignores, request stays active. If user clicks Yes, it gets deactivated.
            if (currentCount == 1) {
                telegramNotificationService.askUserToDeactivateRequest(
                    chatId = request.user.chatId,
                    requestId = request.id
                )
            }
        } else {
            logger.debug("No available trains found for request ${request.id}")
        }
    }
}

