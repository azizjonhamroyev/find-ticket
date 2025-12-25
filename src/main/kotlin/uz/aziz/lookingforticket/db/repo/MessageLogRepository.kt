package uz.aziz.lookingforticket.db.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uz.aziz.lookingforticket.db.entity.MessageLogEntity

@Repository
interface MessageLogRepository : JpaRepository<MessageLogEntity, Long> {
    
    @Query("SELECT m FROM MessageLogEntity m WHERE m.chatId = :chatId ORDER BY m.createdAt DESC")
    fun findByChatIdOrderByCreatedAtDesc(@Param("chatId") chatId: Long): List<MessageLogEntity>
    
    @Query("SELECT m FROM MessageLogEntity m WHERE m.requestId = :requestId ORDER BY m.createdAt DESC")
    fun findByRequestIdOrderByCreatedAtDesc(@Param("requestId") requestId: Long): List<MessageLogEntity>
    
    @Query("SELECT m FROM MessageLogEntity m WHERE m.isSuccess = :isSuccess ORDER BY m.createdAt DESC")
    fun findByIsSuccessOrderByCreatedAtDesc(@Param("isSuccess") isSuccess: Boolean): List<MessageLogEntity>
    
    @Query("SELECT COUNT(m) FROM MessageLogEntity m WHERE m.chatId = :chatId AND m.isSuccess = true")
    fun countSuccessfulMessagesByChatId(@Param("chatId") chatId: Long): Long
    
    @Query("SELECT COUNT(m) FROM MessageLogEntity m WHERE m.chatId = :chatId AND m.isSuccess = false")
    fun countFailedMessagesByChatId(@Param("chatId") chatId: Long): Long
}



