package uz.aziz.lookingforticket.telegram

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uz.aziz.lookingforticket.config.TelegramProperties
import uz.aziz.lookingforticket.db.repo.UserRepository
import uz.aziz.lookingforticket.telegram.dto.response.*
import uz.aziz.lookingforticket.telegram.handler.CommandHandler
import uz.aziz.lookingforticket.telegram.state.UserState
import uz.aziz.lookingforticket.telegram.state.UserStateManager

@Service
class TelegramPollingService(
    private val telegramProperties: TelegramProperties,
    private val webClient: WebClient,
    private val userRepository: UserRepository,
    private val telegramBot: TelegramBot,
    private val commandHandler: CommandHandler,
    private val stateManager: UserStateManager
) {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    private val telegramApiUrl = "https://api.telegram.org/bot${telegramProperties.botToken}"
    private var lastUpdateId: Long = 0
    private var isRunning = false
    
    @PostConstruct
    fun startPolling() {
        logger.info("Starting Telegram bot polling service")
        isRunning = true
        deleteWebhook()
    }
    
    @PreDestroy
    fun stopPolling() {
        logger.info("Stopping Telegram bot polling service")
        isRunning = false
    }
    
    private fun deleteWebhook() {
        try {
            webClient.post()
                .uri("$telegramApiUrl/deleteWebhook")
                .header("Content-Type", "application/json")
                .bodyValue(mapOf("drop_pending_updates" to true))
                .retrieve()
                .bodyToMono(TelegramApiResponse::class.java)
                .block()
            logger.info("Deleted existing webhook (if any) to enable polling")
        } catch (e: Exception) {
            logger.warn("Could not delete webhook: ${e.message}")
        }
    }
    
    @Scheduled(fixedDelay = 1000) // Poll every 1 second
    fun pollUpdates() {
        if (!isRunning) return
        
        try {
            val updates = webClient.get()
                .uri("$telegramApiUrl/getUpdates?offset=${lastUpdateId + 1}&timeout=10")
                .retrieve()
                .bodyToMono(GetUpdatesResponse::class.java)
                .block()
            
            if (updates?.ok == true && updates.result != null) {
                updates.result.forEach { update ->
                    processUpdate(update)
                    lastUpdateId = maxOf(lastUpdateId, update.updateId)
                }
            }
        } catch (e: Exception) {
            logger.error("Error polling updates: ${e.message}", e)
        }
    }
    
    private fun processUpdate(update: TelegramUpdate) {
        logger.debug("Processing update: ${update.updateId}")
        update.message?.let { handleMessage(it) }
        update.callbackQuery?.let { handleCallbackQuery(it) }
    }
    
    private fun handleCallbackQuery(callbackQuery: uz.aziz.lookingforticket.telegram.dto.response.CallbackQuery) {
        val chatId = callbackQuery.message?.chat?.id ?: callbackQuery.from.id
        val data = callbackQuery.data ?: return
        
        logger.info("Received callback query from chat $chatId: $data")
        
        val user = userRepository.findByChatId(chatId)
        if (user == null) {
            answerCallbackQuery(callbackQuery.id)
            return
        }
        
        when {
            data.startsWith("select_station_from_") -> {
                val stationId = data.removePrefix("select_station_from_")
                val stationFromId = commandHandler.handleStationFromSelection(stationId, chatId)
                if (stationFromId != null) {
                    stateManager.setState(chatId, UserState.WAITING_STATION_TO)
                    stateManager.updateStationFrom(chatId, stationFromId)
                }
                // Answer callback query
                answerCallbackQuery(callbackQuery.id)
            }
            data.startsWith("select_station_to_") -> {
                val parts = data.removePrefix("select_station_to_").split("_from_")
                if (parts.size == 2) {
                    val stationToId = parts[0]
                    val stationFromId = parts[1]
                    val result = commandHandler.handleStationToSelection(stationToId, stationFromId, chatId)
                    if (result != null) {
                        stateManager.setState(chatId, UserState.WAITING_FROM_DATE)
                        stateManager.updateStationTo(chatId, result)
                    }
                }
                answerCallbackQuery(callbackQuery.id)
            }
            data.startsWith("deactivate_request_") -> {
                val requestId = data.removePrefix("deactivate_request_").toLongOrNull()
                if (requestId != null) {
                    commandHandler.handleKeepRequestActive(requestId, chatId, false)
                }
                answerCallbackQuery(callbackQuery.id)
            }
            data.startsWith("toggle_brand_") -> {
                val parts = data.removePrefix("toggle_brand_").split("_from_")
                if (parts.size == 2) {
                    val brandIdStr = parts[0]
                    val stationPart = parts[1].split("_to_")
                    if (stationPart.size == 2) {
                        val stationFromId = stationPart[0]
                        val stationToId = stationPart[1]
                        val brandId = brandIdStr.toLongOrNull()
                        if (brandId != null) {
                            commandHandler.handleBrandToggle(brandId, stationFromId, stationToId, chatId)
                        }
                    }
                }
                answerCallbackQuery(callbackQuery.id)
            }
            data.startsWith("finish_brand_selection") -> {
                val parts = if (data.contains("_all_")) {
                    data.removePrefix("finish_brand_selection_all_from_").split("_to_")
                } else {
                    data.removePrefix("finish_brand_selection_from_").split("_to_")
                }
                if (parts.size == 2) {
                    val stationFromId = parts[0]
                    val stationToId = parts[1]
                    val allBrands = data.contains("_all_")
                    // Don't clear state here - handleFinishBrandSelection sets it to WAITING_NUMBER_OF_PEOPLE
                    // State will be cleared after the user enters the number of people
                    commandHandler.handleFinishBrandSelection(
                        stationFromId,
                        stationToId,
                        chatId,
                        user.id,
                        allBrands
                    )
                }
                answerCallbackQuery(callbackQuery.id)
            }
        }
    }
    
    private fun answerCallbackQuery(callbackQueryId: String) {
        try {
            webClient.post()
                .uri("$telegramApiUrl/answerCallbackQuery")
                .header("Content-Type", "application/json")
                .bodyValue(mapOf("callback_query_id" to callbackQueryId))
                .retrieve()
                .bodyToMono(TelegramApiResponse::class.java)
                .block()
        } catch (e: Exception) {
            logger.error("Error answering callback query: ${e.message}", e)
        }
    }
    
    private fun handleMessage(message: Message) {
        val chatId = message.chat.id
        val text = message.text
        
        logger.info("Received message from chat $chatId: $text")
        
        val user = userRepository.findByChatId(chatId)
        val state = stateManager.getState(chatId)
        
        when {
            // Handle commands
            text == "/start" -> {
                commandHandler.handleStartCommand(message)
                stateManager.clearState(chatId)
            }
            text == "/new_request" -> {
                commandHandler.handleNewRequestCommand(message)
                stateManager.setState(chatId, UserState.WAITING_STATION_FROM)
            }
            text == "/my_requests" -> {
                commandHandler.handleMyRequestsCommand(message)
                stateManager.clearState(chatId)
            }
            // Handle state-based inputs
            state == UserState.WAITING_FROM_DATE -> {
                val requestState = stateManager.getRequestState(chatId)
                if (commandHandler.handleFromDateInput(
                        message,
                        user?.id ?: 0L,
                        requestState.stationFromId ?: "",
                        requestState.stationToId ?: ""
                    )
                ) {
                    stateManager.setState(chatId, UserState.WAITING_TO_DATE)
                }
            }
            state == UserState.WAITING_TO_DATE -> {
                val requestState = stateManager.getRequestState(chatId)
                if (commandHandler.handleToDateInput(
                        message,
                        user?.id ?: 0L,
                        requestState.stationFromId ?: "",
                        requestState.stationToId ?: "",
                        requestState.fromDate ?: ""
                    )
                ) {
                    stateManager.setState(chatId, UserState.WAITING_BRAND)
                }
            }
            state == UserState.WAITING_NUMBER_OF_PEOPLE -> {
                logger.info("Handling number of people input for chat $chatId")
                val requestState = stateManager.getRequestState(chatId)
                if (commandHandler.handleNumberOfPeopleInput(
                        message,
                        user?.id ?: 0L,
                        requestState.stationFromId ?: "",
                        requestState.stationToId ?: ""
                    )
                ) {
                    logger.info("Successfully processed number of people input, clearing state for chat $chatId")
                    stateManager.clearState(chatId)
                } else {
                    logger.warn("Failed to process number of people input for chat $chatId")
                }
            }
            else -> {
                logger.debug("Unhandled message: $text")
            }
        }
    }
}
