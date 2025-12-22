package uz.aziz.lookingforticket.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uz.aziz.lookingforticket.db.MessageLogEntity
import uz.aziz.lookingforticket.db.MessageLogRepository
import java.time.LocalDateTime

@Service
class MessageLogService(
    private val messageLogRepository: MessageLogRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @Transactional
    fun logMessage(
        chatId: Long,
        messageText: String,
        messageType: String? = null,
        requestId: Long? = null,
        hasButtons: Boolean = false,
        isSuccess: Boolean,
        errorMessage: String? = null,
        telegramMessageId: Long? = null
    ) {
        try {
            val messageLog = MessageLogEntity(
                chatId = chatId,
                messageText = messageText,
                messageType = messageType,
                requestId = requestId,
                hasButtons = hasButtons,
                isSuccess = isSuccess,
                errorMessage = errorMessage,
                telegramMessageId = telegramMessageId,
                createdAt = LocalDateTime.now()
            )
            
            messageLogRepository.save(messageLog)
            logger.debug("Message log saved: chatId=$chatId, success=$isSuccess, type=$messageType")
        } catch (e: Exception) {
            logger.error("Error saving message log: ${e.message}", e)
        }
    }
}


