package uz.aziz.lookingforticket.db

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    val chatId: Long,

    val username: String? = null,

    val firstName: String? = null,

    val lastName: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    val requests: MutableList<RequestEntity> = mutableListOf()
) {
    // No-arg constructor for JPA
    constructor() : this(chatId = 0)
}

