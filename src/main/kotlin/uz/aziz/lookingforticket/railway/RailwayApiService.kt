package uz.aziz.lookingforticket.railway

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uz.aziz.lookingforticket.config.RailwayUzProperties
import uz.aziz.lookingforticket.db.ApiLogEntity
import uz.aziz.lookingforticket.db.ApiLogRepository
import uz.aziz.lookingforticket.railway.dto.request.TrainAvailabilityRequest
import uz.aziz.lookingforticket.railway.dto.response.*
import uz.aziz.lookingforticket.railway.model.TrainInfo
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
                logger.error("Error checking train availability: ${error.message}", error)
                
                saveLog(
                    url = url,
                    method = "POST",
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    responseStatus = null,
                    responseBody = null,
                    errorMessage = error.message,
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            }
            .onErrorResume { Mono.empty() }
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
        // Make separate API requests for each date in the range
        var currentDate = fromDate
        var combinedMono: Mono<List<TrainInfo>> = Mono.just(emptyList())
        
        while (!currentDate.isAfter(toDate)) {
            val dateMono = checkTrainAvailability(stationFrom, stationTo, currentDate)
                .map { response ->
                    extractAvailableTrains(response, minSeats, brandNames)
                }
                .defaultIfEmpty(emptyList())
            
            combinedMono = combinedMono.flatMap { existingTrains ->
                dateMono.map { newTrains ->
                    (existingTrains + newTrains).distinctBy { it.trainNumber }
                }
            }
            
            currentDate = currentDate.plusDays(1)
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
