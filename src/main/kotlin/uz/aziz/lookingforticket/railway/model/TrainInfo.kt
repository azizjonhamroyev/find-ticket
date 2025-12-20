package uz.aziz.lookingforticket.railway.model

data class TrainInfo(
    val trainNumber: String,
    val trainNumber2: String,
    val brand: String,
    val trainType: String,
    val routeStations: List<String>,
    val carType: String,
    val carTypeShow: String,
    val freeSeats: Int,
    val departureTime: String,
    val departureDate: String,
    val arrivalTime: String,
    val arrivalDate: String,
    val timeInWay: String,
    val minTariff: Long
)

