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
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm")
    private val dateFormatterOnly = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val displayDateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    private fun formatDateTime(dateTime: LocalDateTime) = dateTime.format(displayDateTimeFormatter)

    private fun formatRequestSummary(request: RequestEntity, showStatus: Boolean): String {
        val brandText = requestBrandRepository.findByRequestId(request.id)
            .let { if (it.isEmpty()) "Barcha brendlar" else it.joinToString(", ") { b -> b.brand.displayName } }
        val priceText = if (request.maxPrice == null) "Har qanday" else "Maks. ${String.format("%,d", request.maxPrice)} so'm"
        return buildString {
            append("📋 <b>So'rov №${request.id}</b>\n")
            append("📍 ${request.stationFrom.name} → ${request.stationTo.name}\n")
            append("📅 ${request.fromDate.format(dateTimeFormatter)} - ${request.toDate.format(dateTimeFormatter)}\n")
            append("🚂 Brendlar: $brandText\n")
            append("👥 Kishi soni: ${request.minSeats}\n")
            append("💰 Narx: $priceText\n")
            append("📊 Xabarlar soni: ${request.notificationCount}/2\n")
            request.lastCheckedAt?.let { append("🕐 Oxirgi tekshiruv: ${formatDateTime(it)}\n") }
            request.lastNotifiedAt?.let { append("📬 Oxirgi xabar: ${formatDateTime(it)}\n") }
            if (showStatus) {
                append(if (request.isActive) "✅ Holat: Faol\n" else "❌ Holat: Nofaol\n")
            }
        }
    }

    private companion object {
        const val MSG_START_FIRST = "Iltimos, avval /start buyrug'ini bajaring."
    }

    /** Builds button rows and callback data in 2-column layout. */
    private fun <T> buildTwoColumnButtons(
        items: List<T>,
        buttonText: (T) -> String,
        callbackData: (T) -> String
    ): Pair<List<List<String>>, List<List<String>>> {
        val buttonRows = mutableListOf<List<String>>()
        val callbackRows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var currentCallback = mutableListOf<String>()
        items.forEachIndexed { index, item ->
            currentRow.add(buttonText(item))
            currentCallback.add(callbackData(item))
            if (currentRow.size == 2 || index == items.size - 1) {
                buttonRows.add(currentRow.toList())
                callbackRows.add(currentCallback.toList())
                currentRow = mutableListOf()
                currentCallback = mutableListOf()
            }
        }
        return buttonRows to callbackRows
    }
    
    /**
     * Parses user input for date/time.
     * Supports:
     * - "dd.MM.yyyy HH:mm"
     * - "dd.MM.yyyy" (start of day for from-date, end of day for to-date).
     */
    private fun parseUserDateTime(input: String, isEndOfRange: Boolean): LocalDateTime? {
        val text = input.trim()
        // Try full datetime first
        try {
            return LocalDateTime.parse(text, dateTimeFormatter)
        } catch (_: DateTimeParseException) {
            // Fallback: date-only
        }
        return try {
            val date = LocalDate.parse(text, dateFormatterOnly)
            if (isEndOfRange) {
                date.atTime(23, 59)
            } else {
                date.atStartOfDay()
            }
        } catch (_: DateTimeParseException) {
            null
        }
    }
    
    fun handleStartCommand(message: Message) {
        val chat = message.chat
        val chatId = chat.id
        
        logger.info("Handling /start command from chat $chatId")
        
        val existingUser = userRepository.findByChatId(chatId)
        
        if (existingUser != null) {
            logger.info("User with chatId $chatId already exists")
            telegramBot.sendMessageWithMainMenuBlocking(
                chatId = chatId,
                text = """
                    👋 <b>Assalomu alaykum!</b>
                    
                    Siz allaqachon ro'yxatdan o'tgansiz.
                    
                    Pastdagi tugmalar yordamida so'rovlarni boshqaring.
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
            telegramBot.sendMessageBlocking(chatId, text = MSG_START_FIRST)
            return
        }
        val allRequests = requestRepository.findByUserIdOrderByCreatedAtDesc(user.id)
        if (allRequests.isEmpty()) {
            telegramBot.sendMessageWithMainMenuBlocking(
                chatId = chatId,
                text = """
                    📋 <b>Mening so'rovlarim</b>
                    
                    Hozirda faol so'rovlar mavjud emas.
                """.trimIndent(),
                parseMode = "HTML"
            )
            return
        }

        val (activeRequests, inactiveRequests) = allRequests.partition { it.isActive }
        
        val messageText = buildString {
            append("📋 <b>Mening so'rovlarim</b>\n\n")
            append("Faol: <b>${activeRequests.size}</b> ta\n")
            append("Nofaol: <b>${inactiveRequests.size}</b> ta\n\n")
            if (activeRequests.isNotEmpty()) append("━━━━━━━━ Faol so'rovlar ━━━━━━━━\n\n")
            activeRequests.forEachIndexed { index, request ->
                append(formatRequestSummary(request, showStatus = true))
                append(if (index < activeRequests.size - 1) "\n──────────\n\n" else "\n")
            }
            if (inactiveRequests.isNotEmpty()) {
                if (activeRequests.isNotEmpty()) append("\n")
                append("━━━━━━━━ Nofaol so'rovlar ━━━━━━━━\n\n")
                inactiveRequests.forEachIndexed { index, request ->
                    append(formatRequestSummary(request, showStatus = true))
                    append(if (index < inactiveRequests.size - 1) "\n──────────\n\n" else "\n")
                }
            }
        }

        // Add inline buttons to deactivate / reactivate requests
        val buttons = mutableListOf<List<String>>()
        val callbackDataRows = mutableListOf<List<String>>()
        activeRequests.forEach { request ->
            buttons.add(listOf("⏹ So'rov #${request.id}ni deaktivatsiya qilish"))
            callbackDataRows.add(listOf("deactivate_request_${request.id}"))
        }
        inactiveRequests.forEach { request ->
            buttons.add(listOf("♻️ So'rov #${request.id}ni faollashtirish"))
            callbackDataRows.add(listOf("reactivate_request_${request.id}"))
        }

        if (buttons.isEmpty()) {
            telegramBot.sendMessageWithMainMenuBlocking(
                chatId = chatId,
                text = messageText,
                parseMode = "HTML"
            )
        } else {
            telegramBot.sendMessageWithButtonsBlocking(
                chatId = chatId,
                text = messageText,
                buttons = buttons,
                callbackData = callbackDataRows,
                parseMode = "HTML"
            )
        }
    }
    
    fun handleNewRequestCommand(message: Message) {
        val chatId = message.chat.id
        if (userRepository.findByChatId(chatId) == null) {
            telegramBot.sendMessageBlocking(chatId, text = MSG_START_FIRST)
            return
        }
        val stations = stationRepository.findAllByOrderByName()
        val (buttonRows, callbackDataRows) = buildTwoColumnButtons(stations, { it.name }, { "select_station_from_${it.id}" })
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
        
        val stations = stationRepository.findAllByOrderByName().filter { it.id != stationId }
        val (buttonRows, callbackDataRows) = buildTwoColumnButtons(stations, { it.name }, { "select_station_to_${it.id}_from_$stationId" })
        telegramBot.sendMessageWithButtonsBlocking(
            chatId = chatId,
            text = "✅ Jo'nash stantsiyasi: <b>${station.name}</b>\n\nIltimos, yetib borish stantsiyasini tanlang:",
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
    
    fun handleFromDateInput(message: Message): Boolean {
        val chatId = message.chat.id
        val text = message.text?.trim() ?: return false
        
        val fromDate = parseUserDateTime(text, isEndOfRange = false)
        if (fromDate == null) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "❌ Noto'g'ri sana formati. Iltimos, DD.MM.YYYY yoki DD.MM.YYYY HH:mm formatida kiriting (Masalan: 31.12.2025 yoki 31.12.2025 12:00):"
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
        
        // Save normalized fromDate to state
        stateManager.updateFromDate(chatId, fromDate.format(dateTimeFormatter))
        
        telegramBot.sendMessageBlocking(
            chatId = chatId,
            text = """
                ✅ Boshlanish sanasi: <b>${fromDate.format(dateTimeFormatter)}</b>
                
                📅 Iltimos, jo'nash sanasining tugash sanasini kiriting (format: DD.MM.YYYY yoki DD.MM.YYYY HH:mm):
                (Masalan: 05.01.2026 yoki 05.01.2026 12:00)
            """.trimIndent(),
            parseMode = "HTML"
        )
        
        return true
    }
    
    fun handleToDateInput(message: Message, fromDateStr: String): Boolean {
        val chatId = message.chat.id
        val text = message.text?.trim() ?: return false
        val toDate = parseUserDateTime(text, isEndOfRange = true)
        if (toDate == null) {
            telegramBot.sendMessageBlocking(chatId, text = "❌ Noto'g'ri sana formati. Iltimos, DD.MM.YYYY yoki DD.MM.YYYY HH:mm formatida kiriting (Masalan: 05.01.2026 yoki 05.01.2026 12:00):")
            return false
        }
        val fromDate = try {
            LocalDateTime.parse(fromDateStr, dateTimeFormatter)
        } catch (_: DateTimeParseException) {
            telegramBot.sendMessageBlocking(chatId, text = "❌ Xatolik yuz berdi. Iltimos, qaytadan boshlang.")
            return false
        }
        if (toDate.isBefore(fromDate)) {
            telegramBot.sendMessageBlocking(chatId, text = "❌ Tugash sanasi boshlanish sanasidan oldin bo'lishi mumkin emas. Iltimos, qayta kiriting:")
            return false
        }
        if (toDate.isBefore(LocalDateTime.now())) {
            telegramBot.sendMessageBlocking(chatId, text = "❌ Sana o'tgan sanadan bo'lishi mumkin emas. Iltimos, kelajak sanasini kiriting:")
            return false
        }
        stateManager.updateToDate(chatId, toDate.format(dateTimeFormatter))
        val rs = stateManager.getRequestState(chatId)
        showBrandSelection(chatId, rs.stationFromId ?: "", rs.stationToId ?: "")
        return true
    }
    
    fun showBrandSelection(chatId: Long, stationFromId: String, stationToId: String) {
        val requestState = stateManager.getRequestState(chatId)
        val selectedBrandIds = requestState.selectedBrandIds
        val brands = brandRepository.findAllByOrderByDisplayName()
        val (buttonRows, callbackDataRows) = buildTwoColumnButtons(brands,
            { if (selectedBrandIds.contains(it.id)) "✅ ${it.displayName}" else it.displayName },
            { "toggle_brand_${it.id}_from_${stationFromId}_to_$stationToId" }
        ).let { (rows, callbacks) ->
            val (doneBtn, doneCb) = if (selectedBrandIds.isEmpty())
                listOf("ALL (Barcha brendlar)") to "finish_brand_selection_all_from_${stationFromId}_to_$stationToId"
            else listOf("✅ Tugatish (${selectedBrandIds.size} tanlangan)") to "finish_brand_selection_from_${stationFromId}_to_$stationToId"
            (rows + listOf(doneBtn)) to (callbacks + listOf(listOf(doneCb)))
        }
        val selectedText = if (selectedBrandIds.isEmpty()) "Hech qanday brend tanlanmagan (ALL)"
        else brands.filter { selectedBrandIds.contains(it.id) }.joinToString(", ") { it.displayName }
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
    
    fun handleFinishBrandSelection(stationFromId: String, stationToId: String, chatId: Long): Boolean {
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
    
    fun handleNumberOfPeopleInput(message: Message): Boolean {
        val chatId = message.chat.id
        val text = message.text?.trim() ?: return false
        val numberOfPeople = when (val num = text.toIntOrNull()) {
            null -> {
                telegramBot.sendMessageBlocking(chatId, text = "❌ Noto'g'ri format. Iltimos, raqam kiriting (Masalan: 1, 2, 3):")
                return false
            }
            in 1..10 -> num
            else -> {
                telegramBot.sendMessageBlocking(chatId, text = if (num < 1) "❌ Kishi soni kamida 1 bo'lishi kerak. Iltimos, qayta kiriting:" else "❌ Kishi soni 10 dan oshmasligi kerak. Iltimos, qayta kiriting:")
                return false
            }
        }
        stateManager.updateNumberOfPeople(chatId, numberOfPeople)
        stateManager.setState(chatId, uz.aziz.lookingforticket.telegram.state.UserState.WAITING_PRICE_CHOICE)
        val rs = stateManager.getRequestState(chatId)
        showPriceChoice(chatId, rs.stationFromId ?: "", rs.stationToId ?: "")
        return true
    }
    
    private fun showPriceChoice(chatId: Long, stationFromId: String, stationToId: String) {
        telegramBot.sendMessageWithButtonsBlocking(
            chatId = chatId,
            text = """
                💰 Narx filtri:
                
                Tanlang:
                • <b>Har qanday narx</b> – barcha narxlardagi poyezdlar
                • <b>Maksimal narx</b> – faqat siz ko'rsatgan narxdan arzonroq/o'rinli poyezdlar
            """.trimIndent(),
            buttons = listOf(
                listOf("Har qanday narx", "Maksimal narx")
            ),
            callbackData = listOf(
                listOf("price_any_from_${stationFromId}_to_$stationToId", "price_custom_from_${stationFromId}_to_$stationToId")
            ),
            parseMode = "HTML"
        )
    }
    
    fun handlePriceChoiceCallback(chatId: Long, choice: String, stationFromId: String, stationToId: String): Boolean {
        return when (choice) {
            "any" -> createRequestFromState(chatId, stationFromId, stationToId, maxPrice = null)
            "custom" -> {
                telegramBot.sendMessageBlocking(
                    chatId = chatId,
                    text = "💰 Iltimos, maksimal narxni so'mda kiriting (masalan: 500000):",
                    parseMode = "HTML"
                )
                stateManager.setState(chatId, uz.aziz.lookingforticket.telegram.state.UserState.WAITING_MAX_PRICE)
                true
            }
            else -> false
        }
    }
    
    fun handleMaxPriceInput(message: Message, stationFromId: String, stationToId: String): Boolean {
        val chatId = message.chat.id
        val text = message.text?.trim() ?: return false
        
        val maxPrice = try {
            val value = text.replace(" ", "").toLongOrNull()
            when {
                value == null -> {
                    telegramBot.sendMessageBlocking(
                        chatId = chatId,
                        text = "❌ Noto'g'ri format. Iltimos, raqam kiriting (masalan: 500000):"
                    )
                    return false
                }
                value <= 0 -> {
                    telegramBot.sendMessageBlocking(
                        chatId = chatId,
                        text = "❌ Narx 0 dan katta bo'lishi kerak. Iltimos, qayta kiriting:"
                    )
                    return false
                }
                else -> value
            }
        } catch (_: Exception) {
            telegramBot.sendMessageBlocking(chatId, "❌ Xatolik. Iltimos, raqam kiriting.")
            return false
        }
        
        return createRequestFromState(chatId, stationFromId, stationToId, maxPrice)
    }

    /** Creates request from current state; clears state and sends success message. */
    private fun createRequestFromState(chatId: Long, stationFromId: String, stationToId: String, maxPrice: Long?): Boolean {
        val user = userRepository.findByChatId(chatId) ?: return false
        val stationFrom = stationRepository.findById(stationFromId).orElse(null) ?: return false
        val stationTo = stationRepository.findById(stationToId).orElse(null) ?: return false
        
        val requestState = stateManager.getRequestState(chatId)
        val fromDateStr = requestState.fromDate ?: return false
        val toDateStr = requestState.toDate ?: return false
        val numberOfPeople = requestState.numberOfPeople ?: return false
        
        val fromDate = try {
            LocalDateTime.parse(fromDateStr, dateTimeFormatter)
        } catch (_: Exception) {
            telegramBot.sendMessageBlocking(chatId, "❌ Xatolik yuz berdi. Iltimos, qaytadan boshlang.")
            return false
        }
        
        val toDate = try {
            LocalDateTime.parse(toDateStr, dateTimeFormatter)
        } catch (_: Exception) {
            telegramBot.sendMessageBlocking(chatId, "❌ Xatolik yuz berdi. Iltimos, qaytadan boshlang.")
            return false
        }
        
        val selectedBrandIds = requestState.selectedBrandIds
        
        val request = RequestEntity(
            user = user,
            stationFrom = stationFrom,
            stationTo = stationTo,
            fromDate = fromDate,
            toDate = toDate,
            createdAt = LocalDateTime.now(),
            isActive = true,
            minSeats = numberOfPeople,
            maxPrice = maxPrice,
            notificationCount = 0
        )
        
        val savedRequest = requestRepository.save(request)
        
        if (selectedBrandIds.isNotEmpty()) {
            brandRepository.findAllById(selectedBrandIds).forEach { brand ->
                requestBrandRepository.save(RequestBrandEntity(request = savedRequest, brand = brand))
            }
        }
        
        val brands = if (selectedBrandIds.isEmpty()) emptyList<BrandEntity>() else brandRepository.findAllById(selectedBrandIds)
        val brandText = if (brands.isEmpty()) "Barcha brendlar" else brands.joinToString(", ") { it.displayName }
        val priceText = if (maxPrice == null) "Har qanday narx" else "Maks. ${String.format("%,d", maxPrice)} so'm"
        
        telegramBot.sendMessageBlocking(
            chatId = chatId,
            text = """
                ✅ <b>So'rov muvaffaqiyatli yaratildi!</b>
                
                📋 So'rov №${savedRequest.id}
                📍 ${stationFrom.name} → ${stationTo.name}
                📅 ${fromDate.format(dateTimeFormatter)} - ${toDate.format(dateTimeFormatter)}
                🚂 Brendlar: $brandText
                👥 Kishi soni: <b>$numberOfPeople</b>
                💰 Narx: $priceText
                
                Endi biz sizga shartlarga mos poyezdlar mavjud bo'lganda xabar beramiz!
                
                /my_requests - Mening so'rovlarim
            """.trimIndent(),
            parseMode = "HTML"
        )
        
        logger.info("Created new request ${savedRequest.id} for user ${user.id}, maxPrice=$maxPrice")
        stateManager.clearState(chatId)
        return true
    }
    
    private fun sendRequestNotFound(chatId: Long) {
        telegramBot.sendMessageBlocking(chatId, text = "❌ So'rov topilmadi.")
    }

    fun handleKeepRequestActive(requestId: Long, chatId: Long, keepActive: Boolean) {
        val request = requestRepository.findById(requestId).orElse(null)
        if (request == null || request.user.chatId != chatId) {
            sendRequestNotFound(chatId)
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

    fun handleReactivateRequest(requestId: Long, chatId: Long) {
        val request = requestRepository.findById(requestId).orElse(null)
        if (request == null || request.user.chatId != chatId) {
            sendRequestNotFound(chatId)
            return
        }
        if (request.isActive) {
            telegramBot.sendMessageBlocking(
                chatId = chatId,
                text = "ℹ️ So'rov #$requestId allaqachon faol."
            )
            return
        }

        requestRepository.updateIsActive(requestId, true)
        requestRepository.resetNotificationCount(requestId)
        telegramBot.sendMessageWithMainMenuBlocking(
            chatId = chatId,
            text = """
                ✅ So'rov #$requestId qayta faollashtirildi.
                
                Endi bu so'rov bo'yicha yana tekshiruvlar amalga oshiriladi.
            """.trimIndent(),
            parseMode = "HTML"
        )
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
        
        telegramBot.sendMessageWithMainMenuBlocking(
            chatId = chat.id,
            text = """
                👋 <b>Assalomu alaykum!</b>
                
                Poyezd chiptalarini izlash botiga xush kelibsiz!
                
                Pastdagi tugmalar yordamida yangi so'rov yarating yoki mavjud so'rovlaringizni ko'ring.
            """.trimIndent(),
            parseMode = "HTML"
        )
    }
}

