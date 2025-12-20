package uz.aziz.lookingforticket.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StationRepository : JpaRepository<StationEntity, String> {
    fun findAllByOrderByName(): List<StationEntity>
    fun findByIdIn(ids: List<String>): List<StationEntity>
}

