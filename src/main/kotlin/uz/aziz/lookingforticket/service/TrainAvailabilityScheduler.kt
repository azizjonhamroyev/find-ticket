package uz.aziz.lookingforticket.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import uz.aziz.lookingforticket.config.SchedulerProperties
import uz.aziz.lookingforticket.db.RequestBrandRepository
import uz.aziz.lookingforticket.db.RequestRepository
import uz.aziz.lookingforticket.railway.RailwayApiService
import uz.aziz.lookingforticket.railway.model.TrainInfo
import java.time.Duration
import java.time.LocalDate
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
    fun checkTrainAvailability() {
        logger.debug("Starting scheduled train availability check")
        
        val activeRequests = requestRepository.findByIsActiveTrue()
        
        if (activeRequests.isEmpty()) {
            logger.debug("No active requests to check")
            return
        }
        
        logger.info("Checking ${activeRequests.size} active requests (with ${schedulerProperties.delayBetweenRequestsMs}ms delay between requests)")
        
        // Process requests sequentially with delays to avoid rate limiting (fully async)
        Flux.fromIterable(activeRequests)
            .index()
            .concatMap { tuple ->  // Use concatMap instead of flatMap to ensure sequential processing
                val index = tuple.t1
                val request = tuple.t2
                
                val delay = if (index == 0L) {
                    // No delay for the first request
                    Mono.empty<Unit>()
                } else {
                    // Add delay before processing subsequent requests
                    logger.debug("Waiting ${schedulerProperties.delayBetweenRequestsMs}ms before processing request ${request.id}")
                    Mono.delay(Duration.ofMillis(schedulerProperties.delayBetweenRequestsMs))
                        .then()
                }
                
                delay.then(checkRequestAsync(request))
            }
            .then()
            .subscribe(
                { logger.debug("Completed scheduled train availability check") },
                { error -> logger.error("Error in scheduled train availability check: ${error.message}", error) }
            )
    }
    
    private fun checkRequestAsync(request: uz.aziz.lookingforticket.db.RequestEntity): Mono<Unit> {
        return Mono.fromCallable {
            val currentDate = LocalDate.now()
            
            // Check if to_date is already in the past - deactivate the request
            if (request.toDate.isBefore(currentDate)) {
                logger.info(
                    "Request ${request.id} has expired (to_date: ${request.toDate} < current_date: $currentDate). Deactivating request."
                )
                requestRepository.updateIsActive(request.id, false)
                return@fromCallable null // Skip API call
            }
            
            // Check if from_date is in the past - adjust to current_date
            val effectiveFromDate = if (request.fromDate.isBefore(currentDate)) {
                logger.debug(
                    "Request ${request.id} from_date (${request.fromDate}) is in the past. Adjusting to current_date ($currentDate)"
                )
                currentDate
            } else {
                request.fromDate
            }
            
            // If effective from_date is after to_date, skip API call
            if (effectiveFromDate.isAfter(request.toDate)) {
                logger.info(
                    "Request ${request.id} has no valid dates (effective_from_date: $effectiveFromDate > to_date: ${request.toDate}). Skipping API call."
                )
                return@fromCallable null // Skip API call
            }
            
            logger.debug(
                "Checking request {}: {} -> {} from {} to {} (effective from_date: {})",
                request.id,
                request.stationFrom.name,
                request.stationTo.name,
                request.fromDate,
                request.toDate,
                effectiveFromDate
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
            
            Triple(brandNames, effectiveFromDate, now)
        }
        .flatMap { data ->
            if (data == null) {
                // Request was deactivated or has no valid dates, skip API call
                Mono.just(Pair(emptyList<TrainInfo>(), null as List<String>?))
            } else {
                val (brandNames, effectiveFromDate, now) = data
                // Make async API call (non-blocking) with adjusted from_date
                railwayApiService.getAvailableTrainsWithSeatsForDateRange(
                    stationFrom = request.stationFrom.id,
                    stationTo = request.stationTo.id,
                    fromDate = effectiveFromDate, // Use adjusted from_date
                    toDate = request.toDate,
                    minSeats = request.minSeats,
                    brandNames = brandNames
                )
                .timeout(Duration.ofSeconds(30))
                .map { trains -> Pair(trains, brandNames)
                }
                .defaultIfEmpty(Pair(emptyList(), brandNames))
            }
        }
        .flatMap { (trains, brandNames) ->
            if (trains.isNotEmpty()) {
                logger.info("Found ${trains.size} available trains for request ${request.id}")
                
                Mono.fromCallable {
                    val now = LocalDateTime.now()
                    
                    // Increment notification count
                    requestRepository.incrementNotificationCount(request.id)
                    
                    // Get the updated notification count
                    val updatedRequest = requestRepository.findById(request.id).orElse(null)
                    val newNotificationCount = updatedRequest?.notificationCount ?: request.notificationCount + 1
                    
                    // Update last notified timestamp
                    requestRepository.updateLastNotifiedAt(request.id, now)
                    
                    Pair(newNotificationCount, now)
                }
                .flatMap { (newNotificationCount, now) ->
                    // Send notification to user (reactive)
                    telegramNotificationService.notifyUserAboutAvailableTrains(
                        chatId = request.user.chatId,
                        trains = trains,
                        requestId = request.id
                    )
                    .then(
                        // Ask user to deactivate every 2 notifications (2, 4, 6, 8, etc.)
                        if (newNotificationCount % 2 == 0) {
                            telegramNotificationService.askUserToDeactivateRequest(
                                chatId = request.user.chatId,
                                requestId = request.id,
                                notificationCount = newNotificationCount
                            )
                        } else {
                            Mono.just(Unit)
                        }
                    )
                }
            } else {
                logger.debug("No available trains found for request ${request.id}")
                Mono.just(Unit)
            }
        }
        .onErrorResume { error ->
            logger.error("Error checking request ${request.id}: ${error.message}", error)
            Mono.just(Unit)
        }
    }
}

