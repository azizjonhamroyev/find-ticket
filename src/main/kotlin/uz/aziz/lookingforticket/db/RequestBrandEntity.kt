package uz.aziz.lookingforticket.db

import jakarta.persistence.*

@Entity
@Table(name = "request_brands")
data class RequestBrandEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    val request: RequestEntity,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    val brand: BrandEntity
)




