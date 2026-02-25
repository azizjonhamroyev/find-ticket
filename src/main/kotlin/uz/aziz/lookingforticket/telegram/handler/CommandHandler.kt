package uz.aziz.lookingforticket.telegram.handler

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uz.aziz.lookingforticket.db.entity.BrandEntity
import uz.aziz.lookingforticket.db.entity.RequestBrandEntity
import uz.aziz.lookingforticket.db.entity.RequestEntity
import uz.aziz.lookingforticket.db.entity.UserEntity
import uz.aziz.lookingforticket.db.repo.BrandRepository
import uz.aziz.lookingforticket.db.repo.RequestBrandRepository
import uz.aziz.lookingforticket.db.repo.RequestRepository
import uz.aziz.lookingforticket.db.repo.StationRepository
import uz.aziz.lookingforticket.db.repo.UserRepository
import uz.aziz.lookingforticket.telegram.TelegramBot
import uz.aziz.lookingforticket.telegram.dto.response.Message
import uz.aziz.lookingforticket.telegram.state.UserStateManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Component
class CommandHandler(
    private val userRepository: UserRepository,
    private val stationRepository: StationRepository,
    private val requestRepository: RequestRepository,
    private val brandRepository: BrandRepository,
    private val requestBrandRepository: RequestBrandRepository,
    private val telegramBot: TelegramBot,
    private val stateManager: UserStateManager
) {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    
    fun handleStartCommand(message: Message) {
        val chat = message.chat
        val chatId = chat.id
        
        logger.info("Handling /start command from chat $chatId")
        
        val existingUser = userRepository.findByChatId(chatId)
        
        if (existingUser != null) {
            logger.info("User with chatId $chatId already exists")
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = """
                    👋 <b>Assalomu alaykum!</b>
                    
                    Siz allaqachon ro'yxatdan o'tgansiz.
                    
                    /new_request - Yangi so'rov yaratish
                    /my_requests - Mening so'rovlarim
                """.trimIndent(),
                parseMode = "HTML"
            )
        } else {
            createNewUser(chat)
        }
    }
    
    fun handleMyRequestsCommand(message: Message) {
        val chatId = message.chat.id
        val user = userRepository.findByChatId(chatId)
        
        if (user == null) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "Iltimos, avval /start buyrug'ini bajaring."
            )
            return
        }
        
        val activeRequests = requestRepository.findByUserIdAndIsActiveTrue(user.id)
        
        if (activeRequests.isEmpty()) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = """
                    📋 <b>Mening so'rovlarim</b>
                    
                    Hozirda faol so'rovlar mavjud emas.
                    
                    /new_request - Yangi so'rov yaratish
                """.trimIndent(),
                parseMode = "HTML"
            )
            return
        }
        
        val messageText = buildString {
            append("📋 <b>Mening faol so'rovlarim</b>\n\n")
            append("Jami: <b>${activeRequests.size}</b> ta so'rov\n\n")
            append("━━━━━━━━━━━━━━━━━━━━\n\n")
            
            activeRequests.forEachIndexed { index, request ->
                // Get brands for this request
                val requestBrands = requestBrandRepository.findByRequestId(request.id)
                val brandText = if (requestBrands.isEmpty()) {
                    "Barcha brendlar"
                } else {
                    requestBrands.joinToString(", ") { it.brand.displayName }
                }
                
                // Store nullable values in local variables to avoid smart cast issues
                val lastCheckedAt = request.lastCheckedAt
                val lastNotifiedAt = request.lastNotifiedAt
                
                append("📋 <b>So'rov №${request.id}</b>\n")
                append("📍 ${request.stationFrom.name} → ${request.stationTo.name}\n")
                append("📅 ${request.fromDate.format(dateFormatter)} - ${request.toDate.format(dateFormatter)}\n")
                append("🚂 Brendlar: $brandText\n")
                append("👥 Kishi soni: ${request.minSeats}\n")
                append("📊 Xabarlar soni: ${request.notificationCount}/2\n")
                
                if (lastCheckedAt != null) {
                    append("🕐 Oxirgi tekshiruv: ${formatDateTime(lastCheckedAt)}\n")
                }
                
                if (lastNotifiedAt != null) {
                    append("📬 Oxirgi xabar: ${formatDateTime(lastNotifiedAt)}\n")
                }
                
                val statusEmoji = if (request.isActive) "✅" else "❌"
                append("$statusEmoji Holat: ${if (request.isActive) "Faol" else "Nofaol"}\n")
                
                if (index < activeRequests.size - 1) {
                    append("\n━━━━━━━━━━━━━━━━━━━━\n\n")
                }
            }
        }
        
        telegramBot.sendMessageBlocking(
            chatId = chatId,
            text = messageText,
            parseMode = "HTML"
        )
    }
    
    private fun formatDateTime(dateTime: LocalDateTime): String {
        return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }
    
    fun handleNewRequestCommand(message: Message) {
        val chatId = message.chat.id
        val user = userRepository.findByChatId(chatId)
        
        if (user == null) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "Iltimos, avval /start buyrug'ini bajaring."
            )
            return
        }
        
        val stations = stationRepository.findAllByOrderByName()
        
        // Create buttons for stations (2 columns)
        val buttonRows = mutableListOf<List<String>>()
        val callbackDataRows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var currentCallbackRow = mutableListOf<String>()
        
        stations.forEachIndexed { index, station ->
            currentRow.add(station.name)
            currentCallbackRow.add("select_station_from_${station.id}")
            
            if (currentRow.size == 2 || index == stations.size - 1) {
                buttonRows.add(currentRow.toList())
                callbackDataRows.add(currentCallbackRow.toList())
                currentRow = mutableListOf()
                currentCallbackRow = mutableListOf()
            }
        }
        
        telegramBot.sendMessageWithButtonsBlocking(
            chatId = chatId,
            text = """
                📝 <b>Yangi so'rov yaratish</b>
                
                Iltimos, jo'nash stantsiyasini tanlang:
            """.trimIndent(),
            buttons = buttonRows,
            callbackData = callbackDataRows,
            parseMode = "HTML"
        )
    }
    
    fun handleStationFromSelection(stationId: String, chatId: Long): String? {
        val station = stationRepository.findById(stationId).orElse(null)
        if (station == null) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "❌ Noto'g'ri stantsiya. Iltimos, qayta tanlang."
            )
            return null
        }
        
        val stations = stationRepository.findAllByOrderByName()
            .filter { it.id != stationId }
        
        // Create buttons for destination stations (2 columns)
        val buttonRows = mutableListOf<List<String>>()
        val callbackDataRows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var currentCallbackRow = mutableListOf<String>()
        
        stations.forEachIndexed { index, st ->
            currentRow.add(st.name)
            currentCallbackRow.add("select_station_to_${st.id}_from_${stationId}")
            
            if (currentRow.size == 2 || index == stations.size - 1) {
                buttonRows.add(currentRow.toList())
                callbackDataRows.add(currentCallbackRow.toList())
                currentRow = mutableListOf()
                currentCallbackRow = mutableListOf()
            }
        }
        
        telegramBot.sendMessageWithButtonsBlocking(
            chatId = chatId,
            text = """
                ✅ Jo'nash stantsiyasi: <b>${station.name}</b>
                
                Iltimos, yetib borish stantsiyasini tanlang:
            """.trimIndent(),
            buttons = buttonRows,
            callbackData = callbackDataRows,
            parseMode = "HTML"
        )
        
        return station.id
    }
    
    fun handleStationToSelection(stationId: String, stationFromId: String, chatId: Long): String? {
        val station = stationRepository.findById(stationId).orElse(null)
        if (station == null) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "❌ Noto'g'ri stantsiya. Iltimos, qayta tanlang."
            )
            return null
        }
        
        if (station.id == stationFromId) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "❌ Jo'nash va yetib borish stantsiyalari bir xil bo'lishi mumkin emas. Iltimos, boshqa stantsiya tanlang."
            )
            return null
        }
        
        val stationFrom = stationRepository.findById(stationFromId).orElse(null)!!
        
        telegramBot.sendMessageBlocking(
            chatId = chatId,
            text = """
                ✅ Yetib borish stantsiyasi: <b>${station.name}</b>
                
                📅 Iltimos, jo'nash sanasining boshlanish sanasini kiriting (format: DD.MM.YYYY HH:mm):
                (Masalan: 31.12.2025 12:00)
            """.trimIndent(),
            parseMode = "HTML"
        )
        
        return station.id
    }
    
    fun handleFromDateInput(message: Message, userId: Long, stationFromId: String, stationToId: String): Boolean {
        val chatId = message.chat.id
        val text = message.text?.trim() ?: return false
        
        val fromDate = try {
            LocalDateTime.parse(text, dateFormatter)
        } catch (e: DateTimeParseException) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "❌ Noto'g'ri sana formati. Iltimos, DD.MM.YYYY HH:mm formatida kiriting (Masalan: 31.12.2025 12:00):"
            )
            return false
        }
        
        if (fromDate.isBefore(LocalDateTime.now())) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "❌ Sana o'tgan sanadan bo'lishi mumkin emas. Iltimos, to'g'ri sanasini kiriting:"
            )
            return false
        }
        
        // Save fromDate to state
        stateManager.updateFromDate(chatId, text)
        
        telegramBot.sendMessageBlocking(
            chatId = chatId,
            text = """
                ✅ Boshlanish sanasi: <b>${fromDate.format(dateFormatter)}</b>
                
                📅 Iltimos, jo'nash sanasining tugash sanasini kiriting (format: DD.MM.YYYY HH:mm):
                (Masalan: 05.01.2026 12:00)
            """.trimIndent(),
            parseMode = "HTML"
        )
        
        return true
    }
    
    fun handleToDateInput(message: Message, userId: Long, stationFromId: String, stationToId: String, fromDateStr: String): Boolean {
        val chatId = message.chat.id
        val text = message.text?.trim() ?: return false
        
        val toDate = try {
            LocalDateTime.parse(text, dateFormatter)
        } catch (e: DateTimeParseException) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "❌ Noto'g'ri sana formati. Iltimos, DD.MM.YYYY HH:mm formatida kiriting (Masalan: 05.01.2026 12:00):"
            )
            return false
        }
        
        val fromDate = try {
            LocalDateTime.parse(fromDateStr, dateFormatter)
        } catch (e: DateTimeParseException) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "❌ Xatolik yuz berdi. Iltimos, qaytadan boshlang."
            )
            return false
        }
        
        if (toDate.isBefore(fromDate)) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "❌ Tugash sanasi boshlanish sanasidan oldin bo'lishi mumkin emas. Iltimos, qayta kiriting:"
            )
            return false
        }
        
        if (toDate.isBefore(LocalDateTime.now())) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "❌ Sana o'tgan sanadan bo'lishi mumkin emas. Iltimos, kelajak sanasini kiriting:"
            )
            return false
        }
        
        // Save toDate to state and show brand selection
        stateManager.updateToDate(chatId, toDate.format(dateFormatter))
        
        showBrandSelection(chatId, stationFromId, stationToId)
        
        return true
    }
    
    fun showBrandSelection(chatId: Long, stationFromId: String, stationToId: String) {
        val requestState = stateManager.getRequestState(chatId)
        val selectedBrandIds = requestState.selectedBrandIds
        
        val brands = brandRepository.findAllByOrderByDisplayName()
        val buttonRows = mutableListOf<List<String>>()
        val callbackDataRows = mutableListOf<List<String>>()
        
        // Add brand buttons (2 columns) with checkmarks for selected
        var currentRow = mutableListOf<String>()
        var currentCallbackRow = mutableListOf<String>()
        
        brands.forEachIndexed { index, brand ->
            val isSelected = selectedBrandIds.contains(brand.id)
            val buttonText = if (isSelected) "✅ ${brand.displayName}" else brand.displayName
            currentRow.add(buttonText)
            currentCallbackRow.add("toggle_brand_${brand.id}_from_${stationFromId}_to_${stationToId}")
            
            if (currentRow.size == 2 || index == brands.size - 1) {
                buttonRows.add(currentRow.toList())
                callbackDataRows.add(currentCallbackRow.toList())
                currentRow = mutableListOf()
                currentCallbackRow = mutableListOf()
            }
        }
        
        // Add "Done" button if at least one brand is selected, or allow "ALL" (no selection)
        if (selectedBrandIds.isEmpty()) {
            buttonRows.add(listOf("ALL (Barcha brendlar)"))
            callbackDataRows.add(listOf("finish_brand_selection_all_from_${stationFromId}_to_${stationToId}"))
        } else {
            buttonRows.add(listOf("✅ Tugatish (${selectedBrandIds.size} tanlangan)"))
            callbackDataRows.add(listOf("finish_brand_selection_from_${stationFromId}_to_${stationToId}"))
        }
        
        val selectedText = if (selectedBrandIds.isEmpty()) {
            "Hech qanday brend tanlanmagan (ALL)"
        } else {
            val selectedBrands = brands.filter { selectedBrandIds.contains(it.id) }
            selectedBrands.joinToString(", ") { it.displayName }
        }
        
        telegramBot.sendMessageWithButtonsBlocking(
            chatId = chatId,
            text = """
                ✅ Tugash sanasi: <b>${requestState.toDate}</b>
                
                Iltimos, poyezd brendlarini tanlang (bir nechta tanlash mumkin):
                
                Tanlangan: <b>$selectedText</b>
                
                Brendlarni tanlash/tanlashni bekor qilish uchun tugmalarni bosing.
            """.trimIndent(),
            buttons = buttonRows,
            callbackData = callbackDataRows,
            parseMode = "HTML"
        )
    }
    
    fun handleBrandToggle(brandId: Long, stationFromId: String, stationToId: String, chatId: Long) {
        // Toggle brand selection
        stateManager.toggleBrand(chatId, brandId)
        
        // Show updated brand selection
        showBrandSelection(chatId, stationFromId, stationToId)
    }
    
    fun handleFinishBrandSelection(stationFromId: String, stationToId: String, chatId: Long, userId: Long, allBrands: Boolean = false): Boolean {
        // Ask for number of people instead of creating request immediately
        telegramBot.sendMessageBlocking(
            chatId = chatId,
            text = """
                ✅ Brendlar tanlandi!
                
                👥 Iltimos, necha kishi uchun chipta kerakligini kiriting:
                (Masalan: 1, 2, 3, ...)
            """.trimIndent(),
            parseMode = "HTML"
        )
        
        // Set state to wait for number of people
        stateManager.setState(chatId, uz.aziz.lookingforticket.telegram.state.UserState.WAITING_NUMBER_OF_PEOPLE)
        
        return true
    }
    
    fun handleNumberOfPeopleInput(message: Message, userId: Long, stationFromId: String, stationToId: String): Boolean {
        val chatId = message.chat.id
        val text = message.text?.trim() ?: return false
        
        val numberOfPeople = try {
            val num = text.toInt()
            if (num < 1) {
                telegramBot.sendMessageBlocking(
                    chatId = chatId,
                    text = "❌ Kishi soni kamida 1 bo'lishi kerak. Iltimos, qayta kiriting:"
                )
                return false
            }
            if (num > 10) {
                telegramBot.sendMessageBlocking(
                    chatId = chatId,
                    text = "❌ Kishi soni 10 dan oshmasligi kerak. Iltimos, qayta kiriting:"
                )
                return false
            }
            num
        } catch (_: NumberFormatException) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "❌ Noto'g'ri format. Iltimos, raqam kiriting (Masalan: 1, 2, 3):"
            )
            return false
        }
        
        // Save number of people to state
        stateManager.updateNumberOfPeople(chatId, numberOfPeople)
        
        // Now create the request
        val user = userRepository.findById(userId).orElse(null) ?: return false
        val stationFrom = stationRepository.findById(stationFromId).orElse(null) ?: return false
        val stationTo = stationRepository.findById(stationToId).orElse(null) ?: return false
        
        val requestState = stateManager.getRequestState(chatId)
        val fromDateStr = requestState.fromDate ?: return false
        val toDateStr = requestState.toDate ?: return false
        
        // Parse dates
        val fromDate = try {
            LocalDateTime.parse(fromDateStr, dateFormatter)
        } catch (_: Exception) {
            telegramBot.sendMessageBlocking(chatId, "❌ Xatolik yuz berdi. Iltimos, qaytadan boshlang.")
            return false
        }
        
        val toDate = try {
            LocalDateTime.parse(toDateStr, dateFormatter)
        } catch (_: Exception) {
            telegramBot.sendMessageBlocking(chatId, "❌ Xatolik yuz berdi. Iltimos, qaytadan boshlang.")
            return false
        }
        
        // Get selected brands
        val selectedBrandIds = requestState.selectedBrandIds
        
        val request = RequestEntity(
            user = user,
            stationFrom = stationFrom,
            stationTo = stationTo,
            fromDate = fromDate,
            toDate = toDate,
            createdAt = LocalDateTime.now(),
            isActive = true,
            minSeats = numberOfPeople, // Use number of people as minSeats
            notificationCount = 0
        )
        
        val savedRequest = requestRepository.save(request)
        
        // Save selected brands
        if (selectedBrandIds.isNotEmpty()) {
            val brands = brandRepository.findAllById(selectedBrandIds)
            brands.forEach { brand ->
                val requestBrand = RequestBrandEntity(
                    request = savedRequest,
                    brand = brand
                )
                requestBrandRepository.save(requestBrand)
            }
        }
        
        val brands = if (selectedBrandIds.isEmpty()) {
            emptyList<BrandEntity>()
        } else {
            brandRepository.findAllById(selectedBrandIds)
        }
        
        val brandText = if (brands.isEmpty()) {
            "Barcha brendlar"
        } else {
            brands.joinToString(", ") { it.displayName }
        }
        
        telegramBot.sendMessageBlocking(
            chatId = chatId,
            text = """
                ✅ <b>So'rov muvaffaqiyatli yaratildi!</b>
                
                📋 So'rov №${savedRequest.id}
                📍 ${stationFrom.name} → ${stationTo.name}
                📅 ${fromDate.format(dateFormatter)} - ${toDate.format(dateFormatter)}
                🚂 Brendlar: $brandText
                👥 Kishi soni: <b>$numberOfPeople</b>
                
                Endi biz sizga kamida <b>$numberOfPeople</b> ta bo'sh o'rindiq mavjud bo'lganda xabar beramiz!
                
                /my_requests - Mening so'rovlarim
            """.trimIndent(),
            parseMode = "HTML"
        )
        
        logger.info("Created new request ${savedRequest.id} for user ${user.id} with $numberOfPeople people")
        return true
    }
    
    fun handleKeepRequestActive(requestId: Long, chatId: Long, keepActive: Boolean) {
        val request = requestRepository.findById(requestId).orElse(null)
        if (request == null || request.user.chatId != chatId) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "❌ So'rov topilmadi."
            )
            return
        }
        
        if (keepActive) {
            // User wants to keep it active - reactivate and reset notification count
            requestRepository.updateIsActive(requestId, true)
            requestRepository.resetNotificationCount(requestId)
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = """
                    ✅ So'rov #$requestId faollashtirildi.
                    
                    Xabarlar qayta yuboriladi.
                """.trimIndent(),
                parseMode = "HTML"
            )
        } else {
            // User selected No - ensure it's inactive
            requestRepository.updateIsActive(requestId, false)
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = """
                    ✅ So'rov #$requestId deaktivatsiya qilindi.
                    
                    /new_request - Yangi so'rov yaratish
                """.trimIndent(),
                parseMode = "HTML"
            )
        }
    }
    
    private fun createNewUser(chat: uz.aziz.lookingforticket.telegram.dto.response.Chat) {
        val newUser = UserEntity(
            chatId = chat.id,
            username = chat.username,
            firstName = chat.firstName,
            lastName = chat.lastName,
            createdAt = LocalDateTime.now()
        )
        
        val savedUser = userRepository.save(newUser)
        logger.info("Created new user with id ${savedUser.id} and chatId ${chat.id}")
        
        telegramBot.sendMessageBlocking(
            chatId = chat.id,
            text = """
                👋 <b>Assalomu alaykum!</b>
                
                Poyezd chiptalarini izlash botiga xush kelibsiz!
                
                Botdan foydalanish uchun so'rov yuboring va biz sizga bo'sh o'rindiqlar mavjud bo'lganda xabar beramiz.
                
                /new_request - Yangi so'rov yaratish
                /help - Yordam olish
            """.trimIndent(),
            parseMode = "HTML"
        )
    }
}

