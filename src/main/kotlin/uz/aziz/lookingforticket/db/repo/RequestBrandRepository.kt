package uz.aziz.lookingforticket.db.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uz.aziz.lookingforticket.db.entity.RequestBrandEntity

@Repository
interface RequestBrandRepository : JpaRepository<RequestBrandEntity, Long> {
    fun findByRequestId(requestId: Long): List<RequestBrandEntity>
    
    @Modifying
    @Transactional
    @Query("DELETE FROM RequestBrandEntity rb WHERE rb.request.id = :requestId")
    fun deleteByRequestId(@Param("requestId") requestId: Long)
}




