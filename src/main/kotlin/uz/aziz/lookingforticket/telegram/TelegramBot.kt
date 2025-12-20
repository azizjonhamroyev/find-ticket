package uz.aziz.lookingforticket.telegram

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
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
    
    fun sendMessage(chatId: Long, text: String, parseMode: String? = "HTML"): Boolean {
        return try {
            val request = SendMessageRequest(
                chatId = chatId,
                text = text,
                parseMode = parseMode
            )
            
            val response = webClient.post()
                .uri("$telegramApiUrl/sendMessage")
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TelegramApiResponse::class.java)
                .block()
            
            if (response?.ok == true) {
                logger.info("Successfully sent message to chat $chatId")
                true
            } else {
                logger.error("Failed to send message to chat $chatId: ${response?.description}")
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to send message to chat $chatId: ${e.message}", e)
            false
        }
    }
    
    fun sendMessageWithButtons(
        chatId: Long,
        text: String,
        buttons: List<List<String>>,
        callbackData: List<List<String>>,
        parseMode: String? = "HTML"
    ): Boolean {
        return try {
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
            
            val response = webClient.post()
                .uri("$telegramApiUrl/sendMessage")
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TelegramApiResponse::class.java)
                .block()
            
            if (response?.ok == true) {
                logger.info("Successfully sent message with buttons to chat $chatId")
                true
            } else {
                logger.error("Failed to send message with buttons to chat $chatId: ${response?.description}")
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to send message with buttons to chat $chatId: ${e.message}", e)
            false
        }
    }
}
