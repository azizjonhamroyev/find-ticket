package uz.aziz.lookingforticket.db.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "requests")
data class RequestEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_from_id", nullable = false)
    val stationFrom: StationEntity,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_to_id", nullable = false)
    val stationTo: StationEntity,
    
    @Column(name = "from_date")
    val fromDate: LocalDate,
    
    @Column(name = "to_date")
    val toDate: LocalDate,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "min_seats")
    val minSeats: Int = 1,
    
    @Column(name = "last_checked_at")
    var lastCheckedAt: LocalDateTime? = null,
    
    @Column(name = "last_notified_at")
    var lastNotifiedAt: LocalDateTime? = null,
    
    @Column(name = "notification_count")
    var notificationCount: Int = 0
)
