package uz.aziz.lookingforticket.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ApiLogRepository : JpaRepository<ApiLogEntity, Long>




