package uz.aziz.lookingforticket.telegram.state

enum class UserState {
    IDLE,
    WAITING_STATION_FROM,
    WAITING_STATION_TO,
    WAITING_FROM_DATE,
    WAITING_TO_DATE,
    WAITING_BRAND
}

data class RequestCreationState(
    val stationFromId: String? = null,
    val stationToId: String? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
    val selectedBrandIds: MutableSet<Long> = mutableSetOf()
)

