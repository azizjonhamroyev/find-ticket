package uz.aziz.lookingforticket.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BrandRepository : JpaRepository<BrandEntity, Long> {
    fun findAllByOrderByDisplayName(): List<BrandEntity>
    fun findByName(name: String): BrandEntity?
}




