package uz.aziz.lookingforticket.railway

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import uz.aziz.lookingforticket.config.RailwayUzProperties
import uz.aziz.lookingforticket.db.entity.ApiLogEntity
import uz.aziz.lookingforticket.db.repo.ApiLogRepository
import uz.aziz.lookingforticket.railway.dto.request.DirectionsRequest
import uz.aziz.lookingforticket.railway.dto.request.ForwardDirectionRequest
import uz.aziz.lookingforticket.railway.dto.request.TrainsListRequest
import uz.aziz.lookingforticket.railway.dto.response.*
import uz.aziz.lookingforticket.railway.model.TrainInfo
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class RailwayApiService(
    private val webClient: WebClient,
    private val railwayProperties: RailwayUzProperties,
    private val apiLogRepository: ApiLogRepository,
    private val objectMapper: ObjectMapper
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    /** Parses TrainInfo departure date+time (dd.MM.yyyy, HH:mm) to LocalDateTime for timestamp-range filtering. */
    private fun parseTrainDepartureDateTime(train: TrainInfo): LocalDateTime? {
        val dateStr = train.departureDate
        val timeStr = train.departureTime
        if (dateStr.isBlank()) return null
        return try {
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val time = if (timeStr.isNotBlank()) {
                java.time.LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
            } else {
                java.time.LocalTime.MIN
            }
            LocalDateTime.of(date, time)
        } catch (e: Exception) {
            null
        }
    }
    
    /** Returns true if train's departure is within [fromInclusive, toInclusive]. */
    private fun isTrainDepartureInRange(train: TrainInfo, fromInclusive: LocalDateTime, toInclusive: LocalDateTime): Boolean {
        val dep = parseTrainDepartureDateTime(train) ?: return false
        return !dep.isBefore(fromInclusive) && !dep.isAfter(toInclusive)
    }
    
    fun checkTrainAvailability(
        stationFrom: String,
        stationTo: String,
        depDate: LocalDate,
        userId: Long? = null
    ): Mono<TrainsListApiResponse> {
        val request = TrainsListRequest(
            directions = DirectionsRequest(
                forward = ForwardDirectionRequest.create(
                    depStationCode = stationFrom,
                    arvStationCode = stationTo,
                    depDate = depDate
                )
            )
        )
        return checkTrainAvailabilityWithRequest(request, stationFrom, stationTo, userId)
    }

    private fun checkTrainAvailabilityWithRequest(
        request: TrainsListRequest,
        stationFrom: String,
        stationTo: String,
        userId: Long? = null
    ): Mono<TrainsListApiResponse> {
        val dateInfo = request.directions.forward.date
        logger.debug("Checking train availability: $stationFrom -> $stationTo on $dateInfo")
        
        val url = "${railwayProperties.baseUrl}/api/v3/handbook/trains/list"
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
            if (railwayProperties.cookie.isNotBlank()) append("Cookie: ${railwayProperties.cookie}\n")
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
            .let { spec -> if (railwayProperties.cookie.isNotBlank()) spec.header("Cookie", railwayProperties.cookie) else spec }
        
        return requestSpec
            .bodyValue(request)
            .retrieve()
            .bodyToMono<TrainsListApiResponse>()
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
                val responseBody = try { objectMapper.writeValueAsString(response) } catch (e: Exception) { null }
                saveLog(
                    url = url,
                    method = "POST",
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    responseStatus = 200,
                    responseBody = responseBody,
                    isSuccess = true,
                    executionTimeMs = executionTime,
                    userId = userId
                )
            }
            .onErrorResume { error ->
                val executionTime = System.currentTimeMillis() - startTime
                val (statusCode, errorResponseBody, errorDetail) = buildErrorDetail(error)
                logger.error("Error checking train availability: $errorDetail", error)
                saveLog(
                    url = url,
                    method = "POST",
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    responseStatus = statusCode,
                    responseBody = errorResponseBody,
                    errorMessage = errorDetail,
                    isSuccess = false,
                    executionTimeMs = executionTime,
                    userId = userId
                )
                if (error is WebClientResponseException && error.statusCode == HttpStatus.TOO_MANY_REQUESTS) {
                    logger.error("Rate limited (429) after ${railwayProperties.maxRetries} retries. Skipping: $stationFrom -> $stationTo on $dateInfo")
                }
                Mono.empty()
            }
    }
    
    /** Builds status code, optional response body, and full error message for logging. */
    private fun buildErrorDetail(error: Throwable): Triple<Int?, String?, String> {
        val statusCode = (error as? WebClientResponseException)?.statusCode?.value()
        val responseBody = (error as? WebClientResponseException)?.responseBodyAsString
        val sb = StringBuilder()
        sb.append(error.javaClass.simpleName)
        if (error.message != null) sb.append(": ").append(error.message)
        if (statusCode != null) sb.append(" [HTTP ").append(statusCode).append("]")
        if (!responseBody.isNullOrBlank()) {
            val bodySnippet = if (responseBody.length > 2000) responseBody.take(2000) + "..." else responseBody
            sb.append(" | response: ").append(bodySnippet)
        }
        return Triple(statusCode, responseBody, sb.toString())
    }
    
    /** Parses "25.02.2026 16:00" into date part and time part. */
    private fun parseDateTimeString(dateTimeStr: String?): Pair<String, String> {
        if (dateTimeStr.isNullOrBlank()) return "" to ""
        val parts = dateTimeStr.trim().split(" ", limit = 2)
        return when (parts.size) {
            2 -> parts[0] to parts[1]
            1 -> parts[0] to ""
            else -> "" to ""
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
        executionTimeMs: Long,
        userId: Long? = null
    ) {
        try {
            apiLogRepository.save(
                ApiLogEntity(
                    requestUrl = url,
                    requestMethod = method,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    responseStatus = responseStatus,
                    responseBody = responseBody,
                    errorMessage = errorMessage,
                    isSuccess = isSuccess,
                    executionTimeMs = executionTimeMs,
                    userId = userId
                )
            )
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
        fromDate: LocalDateTime,
        toDate: LocalDateTime,
        minSeats: Int = 1,
        brandNames: List<String>? = null,
        userId: Long? = null,
        maxPrice: Long? = null
    ): Mono<List<TrainInfo>> {
        val startDay = fromDate.toLocalDate()
        val endDay = toDate.toLocalDate()
        var currentDay = startDay
        var combinedMono: Mono<List<TrainInfo>> = Mono.just(emptyList())
        var isFirstRequest = true
        
        while (!currentDay.isAfter(endDay)) {
            val day = currentDay
            val dateMono = if (isFirstRequest) {
                checkTrainAvailability(stationFrom, stationTo, day, userId)
                    .map { extractAvailableTrains(it, minSeats, brandNames) }
                    .defaultIfEmpty(emptyList())
            } else {
                Mono.delay(Duration.ofMillis(railwayProperties.delayBetweenRequestsMs))
                    .then(checkTrainAvailability(stationFrom, stationTo, day, userId))
                    .map { extractAvailableTrains(it, minSeats, brandNames) }
                    .defaultIfEmpty(emptyList())
            }
            combinedMono = combinedMono.flatMap { existing ->
                dateMono.map { new -> (existing + new).distinctBy { it.trainNumber } }
            }
            currentDay = currentDay.plusDays(1)
            isFirstRequest = false
        }
        return combinedMono
            .map { trains -> trains.filter { isTrainDepartureInRange(it, fromDate, toDate) } }
            .map { trains ->
                if (maxPrice != null) trains.filter { it.minTariff <= maxPrice } else trains
            }
    }
    
    private fun extractAvailableTrains(
        response: TrainsListApiResponse,
        minSeats: Int,
        brandNames: List<String>? = null
    ): List<TrainInfo> {
        val trains = mutableListOf<TrainInfo>()
        val trainList = response.data?.directions?.forward?.trains ?: return trains
        
        for (train in trainList) {
            val trainBrand = train.brand ?: ""
            
            if (brandNames != null && !brandNames.contains(trainBrand)) {
                continue
            }
            
            val (depDate, depTime) = parseDateTimeString(train.departureDate)
            val (arvDate, arvTime) = parseDateTimeString(train.arrivalDate)
            val routeStations = listOfNotNull(
                train.subRoute?.depStationName,
                train.subRoute?.arvStationName
            ).filter { it.isNotBlank() }
            
            train.cars?.forEach { car ->
                val freeSeats = car.freeSeats ?: 0
                if (freeSeats >= minSeats) {
                    val minTariff = (car.tariffs?.mapNotNull { it.tariff?.toLong() }?.minOrNull() ?: 0L)
                    trains.add(
                        TrainInfo(
                            trainNumber = train.number ?: "",
                            trainNumber2 = train.number ?: "",
                            brand = trainBrand,
                            trainType = train.type ?: "",
                            routeStations = routeStations.ifEmpty { listOfNotNull(train.originRoute?.depStationName, train.originRoute?.arvStationName).filter { it.isNotBlank() } },
                            carType = car.type ?: "",
                            carTypeShow = car.type ?: "",
                            freeSeats = freeSeats,
                            departureTime = depTime,
                            departureDate = depDate,
                            arrivalTime = arvTime,
                            arrivalDate = arvDate,
                            timeInWay = train.timeOnWay ?: "",
                            minTariff = minTariff
                        )
                    )
                }
            }
        }
        
        return trains.distinctBy { it.trainNumber }
    }
}
