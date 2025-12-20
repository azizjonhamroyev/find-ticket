package uz.aziz.lookingforticket.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByChatId(chatId: Long): UserEntity?
}

