package uz.aziz.lookingforticket.db.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uz.aziz.lookingforticket.db.entity.StationEntity

@Repository
interface StationRepository : JpaRepository<StationEntity, String> {
    fun findAllByOrderByName(): List<StationEntity>
    fun findByIdIn(ids: List<String>): List<StationEntity>
}

