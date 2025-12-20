package uz.aziz.lookingforticket.telegram.state

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class UserStateManager {
    
    private val userStates = ConcurrentHashMap<Long, UserState>()
    private val requestStates = ConcurrentHashMap<Long, RequestCreationState>()
    
    fun setState(userId: Long, state: UserState) {
        userStates[userId] = state
    }
    
    fun getState(userId: Long): UserState {
        return userStates.getOrDefault(userId, UserState.IDLE)
    }
    
    fun clearState(userId: Long) {
        userStates.remove(userId)
        requestStates.remove(userId)
    }
    
    fun getRequestState(userId: Long): RequestCreationState {
        return requestStates.getOrDefault(userId, RequestCreationState())
    }
    
    fun setRequestState(userId: Long, state: RequestCreationState) {
        requestStates[userId] = state
    }
    
    fun updateStationFrom(userId: Long, stationFromId: String) {
        val current = getRequestState(userId)
        setRequestState(userId, current.copy(stationFromId = stationFromId))
    }
    
    fun updateStationTo(userId: Long, stationToId: String) {
        val current = getRequestState(userId)
        setRequestState(userId, current.copy(stationToId = stationToId))
    }
    
    fun updateFromDate(userId: Long, fromDate: String) {
        val current = getRequestState(userId)
        setRequestState(userId, current.copy(fromDate = fromDate))
    }
    
    fun toggleBrand(userId: Long, brandId: Long) {
        val current = getRequestState(userId)
        val newBrandIds = current.selectedBrandIds.toMutableSet()
        if (newBrandIds.contains(brandId)) {
            newBrandIds.remove(brandId)
        } else {
            newBrandIds.add(brandId)
        }
        setRequestState(userId, current.copy(selectedBrandIds = newBrandIds))
    }
    
    fun updateToDate(userId: Long, toDate: String) {
        val current = getRequestState(userId)
        setRequestState(userId, current.copy(toDate = toDate))
    }
}

