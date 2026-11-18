package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.HelplineConfig
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.MoodChoice
import com.example.data.model.UserRole
import com.example.data.repository.ChatRepository
import com.example.data.repository.ReportRepository
import com.example.moderation.CrisisDetector
import com.example.moderation.ModerationFilter
import com.example.moderation.ModerationResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val isLoading: Boolean = true,
    val room: ChatRoom? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val blockedWarning: String? = null,
    val crisisDetected: Boolean = false,
    val remainingSeconds: Long = HelplineConfig.DEFAULT_SESSION_MINUTES * 60L,
    val sessionEnded: Boolean = false,
    val endedNotice: String? = null,
    val actionFeedback: String? = null,
    val errorMessage: String? = null,
    val moodSubmitted: Boolean = false,
    val selectedMood: MoodChoice? = null
) {
    val myRole: UserRole?
        get() = room?.let {
            // Resolved from ViewModel currentUserId
            null
        }
}

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val reportRepository: ReportRepository,
    private val roomId: String,
    val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var crisisLoggedForRoom = false
    private var autoEndTriggered = false
    private var messagesCleanedUp = false

    init {
        observeRoom()
        observeMessages()
        startSessionCountdown()
    }

    private fun observeRoom() {
        viewModelScope.launch {
            chatRepository.observeRoom(roomId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing room $roomId", rootError)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = rootError.localizedMessage ?: "Room load nahi ho saka."
                        )
                    }
                }
                .collect { room ->
                    if (room == null) {
                        _uiState.update { it.copy(isLoading = false) }
                        return@collect
                    }

                    val endsAtMillis = room.endsAt?.toDate()?.time
                    val remainingSec = if (endsAtMillis != null) {
                        ((endsAtMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                    } else {
                        _uiState.value.remainingSeconds
                    }

                    val endedByPartner = room.isEnded() &&
                        room.endedByUid != null &&
                        room.endedByUid != currentUserId

                    if (room.isEnded() && !messagesCleanedUp) {
                        messagesCleanedUp = true
                        viewModelScope.launch {
                            chatRepository.deleteMessagesForRoom(roomId, currentUserId)
                        }
                    }

                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            room = room,
                            remainingSeconds = remainingSec,
                            sessionEnded = state.sessionEnded || room.isEnded(),
                            endedNotice = if (endedByPartner && state.endedNotice == null) {
                                "Aap ke partner ne session khatam kar diya hai."
                            } else {
                                state.endedNotice
                            }
                        )
                    }
                }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.observeMessages(roomId, currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing messages for $roomId", rootError)
                    _uiState.update {
                        it.copy(errorMessage = rootError.localizedMessage ?: "Messages load nahi ho sake.")
                    }
                }
                .collect { messages ->
                    val hasCrisis = messages.any { CrisisDetector.containsCrisisSignal(it.text) }
                    if (hasCrisis && !crisisLoggedForRoom) {
                        crisisLoggedForRoom = true
                        viewModelScope.launch { chatRepository.logCrisisEvent(roomId) }
                    }
                    _uiState.update { state ->
                        state.copy(
                            messages = messages,
                            crisisDetected = state.crisisDetected || hasCrisis
                        )
                    }
                }
        }
    }

    private fun startSessionCountdown() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val room = _uiState.value.room
                if (room == null) continue
                if (_uiState.value.sessionEnded || room.isEnded()) break

                val endsAtMillis = room.endsAt?.toDate()?.time ?: continue
                val remaining = ((endsAtMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                _uiState.update { it.copy(remainingSeconds = remaining) }

                if (remaining == 0L && !autoEndTriggered) {
                    autoEndTriggered = true
                    endSessionEarly(notice = "20 minute ka waqt mukammal ho gaya hai.")
                    break
                }
            }
        }
    }

    fun onInputTextChanged(newText: String) {
        if (newText.length <= HelplineConfig.MAX_MESSAGE_LENGTH) {
            _uiState.update {
                it.copy(
                    inputText = newText,
                    blockedWarning = null
                )
            }
        }
    }

    fun sendQuickPhrase(phrase: String) {
        sendRawMessage(phrase)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText
        sendRawMessage(text)
    }

    private fun sendRawMessage(rawText: String) {
        val room = _uiState.value.room ?: return
        if (_uiState.value.sessionEnded || room.isEnded()) return

        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return

        if (CrisisDetector.containsCrisisSignal(trimmed)) {
            if (!crisisLoggedForRoom) {
                crisisLoggedForRoom = true
                viewModelScope.launch { chatRepository.logCrisisEvent(roomId) }
            }
            _uiState.update { it.copy(crisisDetected = true) }
        }

        when (val modResult = ModerationFilter.check(trimmed)) {
            is ModerationResult.Blocked -> {
                // Keep text in input box so user can edit it
                _uiState.update {
                    it.copy(
                        blockedWarning = "Is message mein personal ya nuksan dene wali baat hai, isliye bheja nahi ja sakta."
                    )
                }
                return
            }

            is ModerationResult.Flagged -> {
                sendToFirestore(room, trimmed, flagged = true, flagReason = modResult.reason)
            }

            ModerationResult.Allowed -> {
                sendToFirestore(room, trimmed, flagged = false, flagReason = null)
            }
        }
    }

    private fun sendToFirestore(
        room: ChatRoom,
        text: String,
        flagged: Boolean,
        flagReason: String?
    ) {
        // Optimistically clear input if sending the typed input
        val isFromInput = text == _uiState.value.inputText.trim()
        if (isFromInput) {
            _uiState.update { it.copy(inputText = "", blockedWarning = null) }
        }

        viewModelScope.launch {
            val result = chatRepository.sendMessage(room, text, flagged, flagReason)
            result.onFailure { error ->
                if (isFromInput) {
                    _uiState.update { it.copy(inputText = text) }
                }
                _uiState.update {
                    it.copy(errorMessage = error.localizedMessage ?: "Message send nahi ho saka.")
                }
            }
        }
    }

    fun requestExtension() {
        val room = _uiState.value.room ?: return
        val myRole = room.roleFor(currentUserId)
        viewModelScope.launch {
            val result = chatRepository.requestOrConfirmExtension(room.id, myRole)
            result.onSuccess {
                _uiState.update {
                    it.copy(actionFeedback = "+10 minute ki darkhwast bhej di gayi hai.")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.localizedMessage ?: "Extension request failed.")
                }
            }
        }
    }

    fun endSessionEarly(notice: String = "Session khatam ho gaya hai.") {
        val currentRoom = _uiState.value.room
        _uiState.update { it.copy(sessionEnded = true, endedNotice = notice) }
        if (currentRoom == null) return

        viewModelScope.launch {
            messagesCleanedUp = true
            chatRepository.endSessionAndDeleteMessages(currentRoom.id)
        }
    }

    fun submitReport(reason: String, includeLastMessages: Boolean) {
        val room = _uiState.value.room ?: return
        val partnerUid = room.partnerUidFor(currentUserId)
        val recent = if (includeLastMessages) {
            _uiState.value.messages.takeLast(5).map { "${it.senderRole}: ${it.text}" }
        } else {
            emptyList()
        }

        viewModelScope.launch {
            val result = reportRepository.submitReport(
                roomId = room.id,
                reportedUid = partnerUid,
                reason = reason,
                lastMessages = recent
            )
            result.onSuccess {
                _uiState.update {
                    it.copy(actionFeedback = "Report mehfooz tareeqe se bhej di gayi hai. Shukriya.")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.localizedMessage ?: "Report submit nahi ho saki.")
                }
            }
        }
    }

    fun blockPartnerAndEnd() {
        val room = _uiState.value.room ?: return
        val partnerUid = room.partnerUidFor(currentUserId)
        viewModelScope.launch {
            reportRepository.blockPartner(partnerUid)
            endSessionEarly(notice = "Aap ne is partner ko block kar diya hai aur chat band ho gayi hai.")
        }
    }

    fun submitMood(mood: MoodChoice) {
        if (_uiState.value.moodSubmitted) return
        _uiState.update { it.copy(selectedMood = mood) }
        viewModelScope.launch {
            val result = chatRepository.submitMoodFeedback(mood)
            result.onSuccess {
                _uiState.update { it.copy(moodSubmitted = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.localizedMessage ?: "Feedback save nahi ho saka.")
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(actionFeedback = null, errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    private companion object {
        const val TAG = "ChatVM"
    }
}
