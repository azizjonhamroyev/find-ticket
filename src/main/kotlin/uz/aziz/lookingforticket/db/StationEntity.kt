package uz.aziz.lookingforticket.db

import jakarta.persistence.*

@Entity
@Table(name = "stations")
class StationEntity(
    @Id
    val id: String = "",
    
    @Column(nullable = false)
    val name: String = ""
) {
    // Default constructor for JPA
    constructor() : this("", "")
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StationEntity) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "StationEntity(id='$id', name='$name')"
    }
}

