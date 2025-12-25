package uz.aziz.lookingforticket.db.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "api_logs")
data class ApiLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "request_url", nullable = false, length = 1000)
    val requestUrl: String,
    
    @Column(name = "request_method", nullable = false, length = 10)
    val requestMethod: String,
    
    @Column(name = "request_headers", columnDefinition = "TEXT")
    val requestHeaders: String? = null,
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_body", columnDefinition = "jsonb")
    val requestBody: String? = null,
    
    @Column(name = "response_status")
    val responseStatus: Int? = null,
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    val responseBody: String? = null,
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,
    
    @Column(name = "is_success")
    val isSuccess: Boolean = false,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "execution_time_ms")
    val executionTimeMs: Long? = null
)

