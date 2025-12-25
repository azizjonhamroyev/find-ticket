package uz.aziz.lookingforticket.db.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uz.aziz.lookingforticket.db.entity.BrandEntity

@Repository
interface BrandRepository : JpaRepository<BrandEntity, Long> {
    fun findAllByOrderByDisplayName(): List<BrandEntity>
    fun findByName(name: String): BrandEntity?
}




