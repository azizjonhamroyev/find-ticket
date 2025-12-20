package uz.aziz.lookingforticket.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uz.aziz.lookingforticket.railway.model.TrainInfo
import uz.aziz.lookingforticket.telegram.TelegramBot

@Service
class TelegramNotificationService(
    private val telegramBot: TelegramBot
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    fun notifyUserAboutAvailableTrains(chatId: Long, trains: List<TrainInfo>, requestId: Long) {
        if (trains.isEmpty()) {
            logger.debug("No trains to notify for request $requestId")
            return
        }
        
        val message = buildTrainAvailabilityMessage(trains, requestId)
        
        telegramBot.sendMessage(chatId, message, "HTML")
    }
    
    private fun buildTrainAvailabilityMessage(trains: List<TrainInfo>, requestId: Long): String {
        val sb = StringBuilder()
        sb.append("🎫 <b>Yangi joylar mavjud!</b>\n\n")
        sb.append("So'rovingiz: <b>#$requestId</b>\n")
        sb.append("Topilgan poyezdlar soni: <b>${trains.size}</b>\n\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n")
        
        trains.forEachIndexed { index, train ->
            sb.append("🚂 <b>${train.trainNumber}</b> - ${train.brand}\n")
            sb.append("📍 ${train.routeStations.joinToString(" → ")}\n")
            sb.append("⏰ ${train.departureTime} (${train.departureDate}) → ${train.arrivalTime} (${train.arrivalDate})\n")
            sb.append("⏱️ Yo'l vaqti: ${train.timeInWay}\n")
            sb.append("💺 Bo'sh o'rindiqlar: <b>${train.freeSeats}</b> (${train.carTypeShow})\n")
            
            if (train.minTariff > 0) {
                sb.append("💰 Minimal narx: ${formatPrice(train.minTariff)} so'm\n")
            }
            
            if (index < trains.size - 1) {
                sb.append("\n━━━━━━━━━━━━━━━━━━━━\n\n")
            }
        }
        
        return sb.toString()
    }
    
    private fun formatPrice(price: Long): String {
        return String.format("%,d", price)
    }
    
    fun askUserToDeactivateRequest(chatId: Long, requestId: Long) {
        val message = """
            ⚠️ <b>Eslatma</b>
            
            Sizning so'rovingiz #$requestId uchun 2 marta xabar yuborildi.
            
            So'rovni deaktivatsiya qilishni xohlaysizmi?
            
            (Agar javob bermasangiz, so'rov faol bo'lib qoladi)
        """.trimIndent()
        
        telegramBot.sendMessageWithButtons(
            chatId = chatId,
            text = message,
            buttons = listOf(
                listOf("Ha")
            ),
            callbackData = listOf(
                listOf("deactivate_request_$requestId")
            )
        )
    }
}

