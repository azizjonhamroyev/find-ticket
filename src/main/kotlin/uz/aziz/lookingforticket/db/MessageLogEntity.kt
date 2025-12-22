package uz.aziz.lookingforticket.db

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "message_logs")
data class MessageLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "chat_id", nullable = false)
    val chatId: Long,

    @Column(name = "message_text", columnDefinition = "TEXT", nullable = false)
    val messageText: String,

    @Column(name = "message_type", length = 50)
    val messageType: String? = null, // e.g., "TRAIN_AVAILABILITY", "DEACTIVATE_REQUEST", etc.

    @Column(name = "request_id")
    val requestId: Long? = null, // Optional: link to request if applicable

    @Column(name = "has_buttons")
    val hasButtons: Boolean = false,

    @Column(name = "is_success", nullable = false)
    val isSuccess: Boolean = false,

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @Column(name = "telegram_message_id")
    val telegramMessageId: Long? = null, // Telegram's message ID if successful

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)



