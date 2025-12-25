package uz.aziz.lookingforticket.db.entity

import jakarta.persistence.*

@Entity
@Table(name = "brands")
class BrandEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(nullable = false, unique = true)
    val name: String = "",
    
    @Column(name = "display_name", nullable = false)
    val displayName: String = ""
) {
    // Default constructor for JPA
    constructor() : this(0, "", "")
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BrandEntity) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "BrandEntity(id=$id, name='$name', displayName='$displayName')"
    }
}




