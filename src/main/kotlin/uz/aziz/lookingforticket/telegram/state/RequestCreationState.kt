package uz.aziz.lookingforticket.telegram.state

data class RequestCreationState(
    val stationFromId: String? = null,
    val stationToId: String? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
    val selectedBrandIds: MutableSet<Long> = mutableSetOf(),
    val numberOfPeople: Int? = null,
    val maxPrice: Long? = null
)
