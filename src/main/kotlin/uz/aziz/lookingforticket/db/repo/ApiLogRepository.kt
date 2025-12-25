package uz.aziz.lookingforticket.db.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uz.aziz.lookingforticket.db.entity.ApiLogEntity

@Repository
interface ApiLogRepository : JpaRepository<ApiLogEntity, Long>




