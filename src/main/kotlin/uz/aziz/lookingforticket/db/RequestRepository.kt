package uz.aziz.lookingforticket.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
interface RequestRepository : JpaRepository<RequestEntity, Long> {
    fun findByIsActiveTrue(): List<RequestEntity>
    
    @Query("SELECT r FROM RequestEntity r WHERE r.user.id = :userId AND r.isActive = true")
    fun findByUserIdAndIsActiveTrue(@Param("userId") userId: Long): List<RequestEntity>
    
    @Modifying
    @Transactional
    @Query("UPDATE RequestEntity r SET r.lastCheckedAt = :checkedAt WHERE r.id = :id")
    fun updateLastCheckedAt(@Param("id") id: Long, @Param("checkedAt") checkedAt: LocalDateTime)
    
    @Modifying
    @Transactional
    @Query("UPDATE RequestEntity r SET r.lastNotifiedAt = :notifiedAt WHERE r.id = :id")
    fun updateLastNotifiedAt(@Param("id") id: Long, @Param("notifiedAt") notifiedAt: LocalDateTime)
    
    @Modifying
    @Transactional
    @Query("UPDATE RequestEntity r SET r.notificationCount = r.notificationCount + 1 WHERE r.id = :id")
    fun incrementNotificationCount(@Param("id") id: Long)
    
    @Modifying
    @Transactional
    @Query("UPDATE RequestEntity r SET r.isActive = :isActive WHERE r.id = :id")
    fun updateIsActive(@Param("id") id: Long, @Param("isActive") isActive: Boolean)
    
    @Modifying
    @Transactional
    @Query("UPDATE RequestEntity r SET r.notificationCount = 0 WHERE r.id = :id")
    fun resetNotificationCount(@Param("id") id: Long)
}

