package uz.aziz.lookingforticket.railway

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import uz.aziz.lookingforticket.config.RailwayUzProperties
import uz.aziz.lookingforticket.db.entity.ApiLogEntity
import uz.aziz.lookingforticket.db.repo.ApiLogRepository
import uz.aziz.lookingforticket.railway.dto.request.TrainAvailabilityRequest
import uz.aziz.lookingforticket.railway.dto.response.*
import uz.aziz.lookingforticket.railway.model.TrainInfo
import java.time.Duration
import java.time.LocalDate

@Service
class RailwayApiService(
    private val webClient: WebClient,
    private val railwayProperties: RailwayUzProperties,
    private val apiLogRepository: ApiLogRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    fun checkTrainAvailability(
        stationFrom: String,
        stationTo: String,
        depDate: LocalDate
    ): Mono<TrainAvailabilityResponse> {
        val request = TrainAvailabilityRequest.create(
            stationFrom = stationFrom,
            stationTo = stationTo,
            depDate = depDate
        )
        
        return checkTrainAvailabilityWithRequest(request, stationFrom, stationTo, depDate.toString())
    }
    
    
    private fun checkTrainAvailabilityWithRequest(
        request: TrainAvailabilityRequest,
        stationFrom: String,
        stationTo: String,
        dateInfo: String
    ): Mono<TrainAvailabilityResponse> {
        logger.debug("Checking train availability: $stationFrom -> $stationTo on $dateInfo")
        
        val url = "${railwayProperties.baseUrl}/api/v3/trains/availability/space/between/stations"
        val startTime = System.currentTimeMillis()
        
        val requestBody = try {
            objectMapper.writeValueAsString(request)
        } catch (e: Exception) {
            logger.error("Error serializing request: ${e.message}", e)
            null
        }
        
        val requestHeaders = buildString {
            append("Accept: application/json\n")
            append("Accept-Language: uz\n")
            append("Content-Type: application/json\n")
            append("Origin: ${railwayProperties.baseUrl}\n")
            append("Referer: ${railwayProperties.baseUrl}/uz/home\n")
            append("X-XSRF-TOKEN: ${railwayProperties.xsrfToken}\n")
            if (railwayProperties.cookie.isNotBlank()) {
                append("Cookie: ${railwayProperties.cookie}\n")
            }
        }
        
        val requestSpec = webClient.post()
            .uri(url)
            .header("Accept", "application/json")
            .header("Accept-Language", "uz")
            .header("Content-Type", "application/json")
            .header("Origin", railwayProperties.baseUrl)
            .header("Referer", "${railwayProperties.baseUrl}/uz/home")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
            .header("device-type", "BROWSER")
            .header("X-XSRF-TOKEN", railwayProperties.xsrfToken)
            .cookie("XSRF-TOKEN", railwayProperties.xsrfToken)
        
        val finalRequestSpec = if (railwayProperties.cookie.isNotBlank()) {
            requestSpec.header("Cookie", railwayProperties.cookie)
        } else {
            requestSpec
        }
        
        return finalRequestSpec
            .bodyValue(request)
            .retrieve()
            .bodyToMono(TrainAvailabilityResponse::class.java)
            .retryWhen(
                Retry.backoff(railwayProperties.maxRetries.toLong(), Duration.ofMillis(railwayProperties.initialRetryDelayMs))
                    .maxBackoff(Duration.ofMillis(railwayProperties.maxRetryDelayMs))
                    .filter { throwable ->
                        // Only retry on 429 (Too Many Requests) errors
                        if (throwable is WebClientResponseException) {
                            throwable.statusCode == HttpStatus.TOO_MANY_REQUESTS
                        } else {
                            false
                        }
                    }
                    .doBeforeRetry { retrySignal ->
                        val attempt = retrySignal.totalRetries() + 1
                        // Calculate expected delay: exponential backoff (min(initialDelay * 2^attempt, maxDelay))
                        val expectedDelayMs = minOf(
                            railwayProperties.initialRetryDelayMs * (1L shl attempt.toInt()),
                            railwayProperties.maxRetryDelayMs
                        )
                        val expectedDelaySeconds = expectedDelayMs / 1000.0
                        logger.warn(
                            "Rate limited (429). Retrying attempt $attempt/${railwayProperties.maxRetries} " +
                            "(expected delay: ${expectedDelaySeconds}s). " +
                            "Request: $stationFrom -> $stationTo on $dateInfo"
                        )
                    }
            )
            .doOnSuccess { response ->
                val executionTime = System.currentTimeMillis() - startTime
                val responseBody = try {
                    objectMapper.writeValueAsString(response)
                } catch (e: Exception) {
                    null
                }
                
                saveLog(
                    url = url,
                    method = "POST",
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    responseStatus = 200,
                    responseBody = responseBody,
                    isSuccess = true,
                    executionTimeMs = executionTime
                )
            }
            .doOnError { error ->
                val executionTime = System.currentTimeMillis() - startTime
                val statusCode = if (error is WebClientResponseException) {
                    error.statusCode.value()
                } else {
                    null
                }
                
                logger.error("Error checking train availability: ${error.message}", error)
                
                saveLog(
                    url = url,
                    method = "POST",
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    responseStatus = statusCode,
                    responseBody = null,
                    errorMessage = error.message,
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            }
            .onErrorResume { error ->
                // If it's a 429 after all retries, log and return empty
                if (error is WebClientResponseException && error.statusCode == HttpStatus.TOO_MANY_REQUESTS) {
                    logger.error(
                        "Rate limited (429) after ${railwayProperties.maxRetries} retries. " +
                        "Skipping request: $stationFrom -> $stationTo on $dateInfo"
                    )
                }
                Mono.empty()
            }
    }
    
    @Transactional
    fun saveLog(
        url: String,
        method: String,
        requestHeaders: String?,
        requestBody: String?,
        responseStatus: Int?,
        responseBody: String?,
        errorMessage: String? = null,
        isSuccess: Boolean,
        executionTimeMs: Long
    ) {
        try {
            val log = ApiLogEntity(
                requestUrl = url,
                requestMethod = method,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                responseStatus = responseStatus,
                responseBody = responseBody,
                errorMessage = errorMessage,
                isSuccess = isSuccess,
                executionTimeMs = executionTimeMs
            )
            apiLogRepository.save(log)
        } catch (e: Exception) {
            logger.error("Error saving API log: ${e.message}", e)
        }
    }
    
    fun getAvailableTrainsWithSeats(
        stationFrom: String,
        stationTo: String,
        depDate: LocalDate,
        minSeats: Int = 1
    ): Mono<List<TrainInfo>> {
        return checkTrainAvailability(stationFrom, stationTo, depDate)
            .map { response ->
                extractAvailableTrains(response, minSeats)
            }
            .defaultIfEmpty(emptyList())
    }
    
    fun getAvailableTrainsWithSeatsForDateRange(
        stationFrom: String,
        stationTo: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        minSeats: Int = 1,
        brandNames: List<String>? = null
    ): Mono<List<TrainInfo>> {
        // Make separate API requests for each date in the range with delays between requests
        var currentDate = fromDate
        var combinedMono: Mono<List<TrainInfo>> = Mono.just(emptyList())
        var isFirstRequest = true
        
        while (!currentDate.isAfter(toDate)) {
            val dateMono = if (isFirstRequest) {
                // No delay for the first request
                checkTrainAvailability(stationFrom, stationTo, currentDate)
                    .map { response ->
                        extractAvailableTrains(response, minSeats, brandNames)
                    }
                    .defaultIfEmpty(emptyList())
            } else {
                // Add delay before subsequent requests to avoid rate limiting
                Mono.delay(Duration.ofMillis(railwayProperties.delayBetweenRequestsMs))
                    .then(checkTrainAvailability(stationFrom, stationTo, currentDate))
                    .map { response ->
                        extractAvailableTrains(response, minSeats, brandNames)
                    }
                    .defaultIfEmpty(emptyList())
            }
            
            combinedMono = combinedMono.flatMap { existingTrains ->
                dateMono.map { newTrains ->
                    (existingTrains + newTrains).distinctBy { it.trainNumber }
                }
            }
            
            currentDate = currentDate.plusDays(1)
            isFirstRequest = false
        }
        
        return combinedMono
    }
    
    private fun extractAvailableTrains(
        response: TrainAvailabilityResponse,
        minSeats: Int,
        brandNames: List<String>? = null
    ): List<TrainInfo> {
        val trains = mutableListOf<TrainInfo>()
        
        response.express?.direction?.forEach { direction ->
            direction.trains?.forEach { trainList ->
                trainList.train?.forEach { train ->
                    val trainBrand = train.brand ?: ""
                    
                    // Filter by brands if specified (train brand must match any of the selected brands)
                    if (brandNames != null && !brandNames.contains(trainBrand)) {
                        return@forEach
                    }
                    
                    train.places?.cars?.forEach { car ->
                        val freeSeats = car.freeSeats?.toIntOrNull() ?: 0
                        
                        if (freeSeats >= minSeats) {
                            val minTariff = car.tariffs?.tariff?.mapNotNull { 
                                it.tariff?.toLongOrNull() 
                            }?.minOrNull() ?: 0L
                            
                            trains.add(
                                TrainInfo(
                                    trainNumber = train.number ?: "",
                                    trainNumber2 = train.number2 ?: train.number ?: "",
                                    brand = trainBrand,
                                    trainType = train.type ?: "",
                                    routeStations = train.route?.station ?: emptyList(),
                                    carType = car.type ?: "",
                                    carTypeShow = car.typeShow ?: "",
                                    freeSeats = freeSeats,
                                    departureTime = train.departure?.localTime ?: "",
                                    departureDate = train.departure?.localDate ?: "",
                                    arrivalTime = train.arrival?.localTime ?: "",
                                    arrivalDate = train.arrival?.localDate ?: "",
                                    timeInWay = train.timeInWay ?: "",
                                    minTariff = minTariff
                                )
                            )
                        }
                    }
                }
            }
        }
        
        return trains.distinctBy { it.trainNumber }
    }
}
