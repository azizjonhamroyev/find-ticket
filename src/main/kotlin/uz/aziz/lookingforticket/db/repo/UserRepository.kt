package uz.aziz.lookingforticket.db.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uz.aziz.lookingforticket.db.entity.UserEntity

@Repository
interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByChatId(chatId: Long): UserEntity?
}

