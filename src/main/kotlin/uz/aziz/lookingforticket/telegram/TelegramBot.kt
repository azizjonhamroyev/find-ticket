package uz.aziz.lookingforticket.telegram

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uz.aziz.lookingforticket.config.TelegramProperties
import uz.aziz.lookingforticket.telegram.dto.request.SendMessageRequest
import uz.aziz.lookingforticket.telegram.dto.response.TelegramApiResponse

@Component
class TelegramBot(
    private val telegramProperties: TelegramProperties,
    private val webClient: WebClient
) {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    private val telegramApiUrl = "https://api.telegram.org/bot${telegramProperties.botToken}"
    
    data class SendMessageResult(
        val isSuccess: Boolean,
        val errorMessage: String? = null,
        val telegramMessageId: Long? = null
    )
    
    fun sendMessage(chatId: Long, text: String, parseMode: String? = "HTML"): Mono<SendMessageResult> {
        val request = SendMessageRequest(
            chatId = chatId,
            text = text,
            parseMode = parseMode
        )
        
        return webClient.post()
            .uri("$telegramApiUrl/sendMessage")
            .header("Content-Type", "application/json")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<TelegramApiResponse>()
            .map { response ->
                if (response.ok) {
                    val messageId = response.result?.messageId
                    logger.info("Successfully sent message to chat $chatId (messageId: $messageId)")
                    SendMessageResult(
                        isSuccess = true,
                        telegramMessageId = messageId
                    )
                } else {
                    val errorMsg = response.description ?: "Unknown error"
                    logger.error("Failed to send message to chat $chatId: $errorMsg")
                    SendMessageResult(
                        isSuccess = false,
                        errorMessage = errorMsg
                    )
                }
            }
            .onErrorResume { error ->
                val detail = buildSendErrorDetail(error)
                logger.error("Failed to send message to chat $chatId: $detail", error)
                Mono.just(SendMessageResult(isSuccess = false, errorMessage = detail))
            }
    }
    
    /**
     * Sends a message with a persistent main menu (reply keyboard) that always shows:
     * - Yangi so'rov yaratish
     * - Mening so'rovlarim
     */
    fun sendMessageWithMainMenu(
        chatId: Long,
        text: String,
        parseMode: String? = "HTML"
    ): Mono<SendMessageResult> {
        val replyKeyboard = listOf(
            listOf(mapOf("text" to "Yangi so'rov yaratish")),
            listOf(mapOf("text" to "Mening so'rovlarim"))
        )
        
        val request = mapOf(
            "chat_id" to chatId,
            "text" to text,
            "parse_mode" to parseMode,
            "reply_markup" to mapOf(
                "keyboard" to replyKeyboard,
                "resize_keyboard" to true,
                "one_time_keyboard" to false
            )
        )
        
        return webClient.post()
            .uri("$telegramApiUrl/sendMessage")
            .header("Content-Type", "application/json")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<TelegramApiResponse>()
            .map { response ->
                if (response.ok) {
                    val messageId = response.result?.messageId
                    logger.info("Successfully sent message with main menu to chat $chatId (messageId: $messageId)")
                    SendMessageResult(
                        isSuccess = true,
                        telegramMessageId = messageId
                    )
                } else {
                    val errorMsg = response.description ?: "Unknown error"
                    logger.error("Failed to send message with main menu to chat $chatId: $errorMsg")
                    SendMessageResult(
                        isSuccess = false,
                        errorMessage = errorMsg
                    )
                }
            }
            .onErrorResume { error ->
                val detail = buildSendErrorDetail(error)
                logger.error("Failed to send message with main menu to chat $chatId: $detail", error)
                Mono.just(SendMessageResult(isSuccess = false, errorMessage = detail))
            }
    }
    
    /** Builds a detailed error string for logging and message_logs. */
    private fun buildSendErrorDetail(error: Throwable): String {
        val sb = StringBuilder()
        sb.append(error.javaClass.simpleName)
        if (error.message != null) sb.append(": ").append(error.message)
        (error as? WebClientResponseException)?.let { ex ->
            sb.append(" [HTTP ").append(ex.statusCode.value()).append("]")
            val body = ex.responseBodyAsString
            if (!body.isNullOrBlank()) {
                val snippet = if (body.length > 1500) body.take(1500) + "..." else body
                sb.append(" | response: ").append(snippet)
            }
        }
        error.cause?.let { cause ->
            sb.append(" | cause: ").append(cause.javaClass.simpleName).append(": ").append(cause.message)
        }
        return sb.toString()
    }
    
    fun sendMessageWithButtons(
        chatId: Long,
        text: String,
        buttons: List<List<String>>,
        callbackData: List<List<String>>,
        parseMode: String? = "HTML"
    ): Mono<SendMessageResult> {
        val inlineKeyboard = buttons.zip(callbackData).map { (buttonRow, dataRow) ->
            buttonRow.zip(dataRow).map { (buttonText, data) ->
                mapOf(
                    "text" to buttonText,
                    "callback_data" to data
                )
            }
        }
        
        val request = mapOf(
            "chat_id" to chatId,
            "text" to text,
            "parse_mode" to parseMode,
            "reply_markup" to mapOf(
                "inline_keyboard" to inlineKeyboard
            )
        )
        
        return webClient.post()
            .uri("$telegramApiUrl/sendMessage")
            .header("Content-Type", "application/json")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<TelegramApiResponse>()
            .map { response ->
                if (response.ok) {
                    val messageId = 0L
                    logger.info("Successfully sent message with buttons to chat $chatId (messageId: $messageId)")
                    SendMessageResult(
                        isSuccess = true,
                        telegramMessageId = messageId
                    )
                } else {
                    val errorMsg = response.description ?: "Unknown error"
                    logger.error("Failed to send message with buttons to chat $chatId: $errorMsg")
                    SendMessageResult(
                        isSuccess = false,
                        errorMessage = errorMsg
                    )
                }
            }
            .onErrorResume { error ->
                val detail = buildSendErrorDetail(error)
                logger.error("Failed to send message with buttons to chat $chatId: $detail", error)
                Mono.just(SendMessageResult(isSuccess = false, errorMessage = detail))
            }
    }

    fun sendMessageBlocking(chatId: Long, text: String, parseMode: String? = "HTML"): SendMessageResult =
        sendMessage(chatId, text, parseMode).block()
            ?: SendMessageResult(isSuccess = false, errorMessage = "Failed to send message")
    
    fun sendMessageWithMainMenuBlocking(chatId: Long, text: String, parseMode: String? = "HTML"): SendMessageResult =
        sendMessageWithMainMenu(chatId, text, parseMode).block()
            ?: SendMessageResult(isSuccess = false, errorMessage = "Failed to send message with main menu")
    
    fun sendMessageWithButtonsBlocking(
        chatId: Long,
        text: String,
        buttons: List<List<String>>,
        callbackData: List<List<String>>,
        parseMode: String? = "HTML"
    ): SendMessageResult =
        sendMessageWithButtons(chatId, text, buttons, callbackData, parseMode).block()
            ?: SendMessageResult(isSuccess = false, errorMessage = "Failed to send message with buttons")
}
