package uz.aziz.lookingforticket.telegram

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
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
            .bodyToMono(TelegramApiResponse::class.java)
            .map { response ->
                if (response.ok == true) {
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
                logger.error("Failed to send message to chat $chatId: ${error.message}", error)
                Mono.just(
                    SendMessageResult(
                        isSuccess = false,
                        errorMessage = error.message
                    )
                )
            }
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
            .bodyToMono(TelegramApiResponse::class.java)
            .map { response ->
                if (response.ok == true) {
                    val messageId = response.result?.messageId
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
                logger.error("Failed to send message with buttons to chat $chatId: ${error.message}", error)
                Mono.just(
                    SendMessageResult(
                        isSuccess = false,
                        errorMessage = error.message
                    )
                )
            }
    }
    
    // Blocking wrappers for non-reactive contexts (e.g., CommandHandler)
    // These can be used when not in a reactive chain
    fun sendMessageBlocking(chatId: Long, text: String, parseMode: String? = "HTML"): SendMessageResult {
        return sendMessage(chatId, text, parseMode).block() ?: SendMessageResult(
            isSuccess = false,
            errorMessage = "Failed to send message"
        )
    }
    
    fun sendMessageWithButtonsBlocking(
        chatId: Long,
        text: String,
        buttons: List<List<String>>,
        callbackData: List<List<String>>,
        parseMode: String? = "HTML"
    ): SendMessageResult {
        return sendMessageWithButtons(chatId, text, buttons, callbackData, parseMode).block() ?: SendMessageResult(
            isSuccess = false,
            errorMessage = "Failed to send message with buttons"
        )
    }
}
