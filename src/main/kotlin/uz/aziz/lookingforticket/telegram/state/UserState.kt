package uz.aziz.lookingforticket.telegram.state

enum class UserState {
    IDLE,
    WAITING_STATION_FROM,
    WAITING_STATION_TO,
    WAITING_FROM_DATE,
    WAITING_TO_DATE,
    WAITING_BRAND,
    WAITING_NUMBER_OF_PEOPLE,
    WAITING_PRICE_CHOICE,
    WAITING_MAX_PRICE
}
