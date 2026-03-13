package uz.aziz.lookingforticket.telegram

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
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
                .bodyToMono<TelegramApiResponse>()
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
                .bodyToMono<GetUpdatesResponse>()
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
    
    private fun handleCallbackQuery(callbackQuery: CallbackQuery) {
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
                val stationFromId = commandHandler.handleStationFromSelection(data.removePrefix("select_station_from_"), chatId)
                if (stationFromId != null) {
                    stateManager.setState(chatId, UserState.WAITING_STATION_TO)
                    stateManager.updateStationFrom(chatId, stationFromId)
                }
            }
            data.startsWith("select_station_to_") -> {
                val parts = data.removePrefix("select_station_to_").split("_from_")
                if (parts.size == 2) {
                    val result = commandHandler.handleStationToSelection(parts[0], parts[1], chatId)
                    if (result != null) {
                        stateManager.setState(chatId, UserState.WAITING_FROM_DATE)
                        stateManager.updateStationTo(chatId, result)
                    }
                }
            }
            data.startsWith("deactivate_request_") -> {
                data.removePrefix("deactivate_request_").toLongOrNull()?.let {
                    commandHandler.handleKeepRequestActive(it, chatId, false)
                }
            }
            data.startsWith("reactivate_request_") -> {
                data.removePrefix("reactivate_request_").toLongOrNull()?.let {
                    commandHandler.handleReactivateRequest(it, chatId)
                }
            }
            data.startsWith("toggle_brand_") -> {
                val parts = data.removePrefix("toggle_brand_").split("_from_")
                if (parts.size == 2) {
                    val stationPart = parts[1].split("_to_")
                    if (stationPart.size == 2) {
                        parts[0].toLongOrNull()?.let { brandId ->
                            commandHandler.handleBrandToggle(brandId, stationPart[0], stationPart[1], chatId)
                        }
                    }
                }
            }
            data.startsWith("finish_brand_selection") -> {
                val parts = if (data.contains("_all_")) data.removePrefix("finish_brand_selection_all_from_").split("_to_")
                else data.removePrefix("finish_brand_selection_from_").split("_to_")
                if (parts.size == 2) commandHandler.handleFinishBrandSelection(parts[0], parts[1], chatId)
            }
            data.startsWith("price_any_from_") -> {
                val rest = data.removePrefix("price_any_from_").split("_to_")
                if (rest.size == 2) commandHandler.handlePriceChoiceCallback(chatId, "any", rest[0], rest[1])
            }
            data.startsWith("price_custom_from_") -> {
                val rest = data.removePrefix("price_custom_from_").split("_to_")
                if (rest.size == 2) commandHandler.handlePriceChoiceCallback(chatId, "custom", rest[0], rest[1])
            }
        }
        answerCallbackQuery(callbackQuery.id)
    }
    
    private fun answerCallbackQuery(callbackQueryId: String) {
        try {
            webClient.post()
                .uri("$telegramApiUrl/answerCallbackQuery")
                .header("Content-Type", "application/json")
                .bodyValue(mapOf("callback_query_id" to callbackQueryId))
                .retrieve()
                .bodyToMono(Any::class.java)
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
            // Handle commands (also support localized button labels)
            text == "/start" -> {
                commandHandler.handleStartCommand(message)
                stateManager.clearState(chatId)
            }
            text == "/new_request" || text == "Yangi so'rov yaratish" -> {
                commandHandler.handleNewRequestCommand(message)
                stateManager.setState(chatId, UserState.WAITING_STATION_FROM)
            }
            text == "/my_requests" || text == "Mening so'rovlarim" -> {
                commandHandler.handleMyRequestsCommand(message)
                stateManager.clearState(chatId)
            }
            // Handle state-based inputs
            state == UserState.WAITING_FROM_DATE -> {
                val rs = stateManager.getRequestState(chatId)
                if (commandHandler.handleFromDateInput(message)) {
                    stateManager.setState(chatId, UserState.WAITING_TO_DATE)
                }
            }
            state == UserState.WAITING_TO_DATE -> {
                val rs = stateManager.getRequestState(chatId)
                if (commandHandler.handleToDateInput(message, rs.fromDate ?: "")) {
                    stateManager.setState(chatId, UserState.WAITING_BRAND)
                }
            }
            state == UserState.WAITING_NUMBER_OF_PEOPLE -> {
                val rs = stateManager.getRequestState(chatId)
                commandHandler.handleNumberOfPeopleInput(message)
            }
            state == UserState.WAITING_MAX_PRICE -> {
                val rs = stateManager.getRequestState(chatId)
                if (commandHandler.handleMaxPriceInput(message, rs.stationFromId ?: "", rs.stationToId ?: "")) {
                    stateManager.clearState(chatId)
                }
            }
            else -> {
                logger.debug("Unhandled message: $text")
            }
        }
    }
}
