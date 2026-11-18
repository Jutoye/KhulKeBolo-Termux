package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.HelplineConfig
import com.example.data.model.UserRole
import com.example.data.repository.MatchRepository
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

sealed interface MatchUiState {
    data object Idle : MatchUiState
    data class Searching(
        val role: UserRole,
        val secondsRemaining: Int = HelplineConfig.MATCH_TIMEOUT_SECONDS
    ) : MatchUiState

    data class Matched(val roomId: String) : MatchUiState
    data class TimedOut(val role: UserRole) : MatchUiState
    data class Error(val message: String) : MatchUiState
}

class MatchViewModel(
    private val matchRepository: MatchRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<MatchUiState>(MatchUiState.Idle)
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var observerJob: Job? = null

    fun startMatching(role: UserRole) {
        if (_uiState.value is MatchUiState.Searching) return
        stopJobs()

        _uiState.value = MatchUiState.Searching(
            role = role,
            secondsRemaining = HelplineConfig.MATCH_TIMEOUT_SECONDS
        )

        observeForPartnerMatch()
        startTimeoutCountdown(role)

        viewModelScope.launch {
            val result = matchRepository.enterQueueAndTryMatch(role)
            result.onSuccess { matchedRoomId ->
                if (!matchedRoomId.isNullOrBlank()) {
                    stopJobs()
                    _uiState.value = MatchUiState.Matched(matchedRoomId)
                }
            }.onFailure { error ->
                stopJobs()
                _uiState.value = MatchUiState.Error(
                    error.localizedMessage ?: "Matchmaking shuru nahi ho saki. Dobara koshish karein."
                )
            }
        }
    }

    private fun observeForPartnerMatch() {
        observerJob = viewModelScope.launch {
            matchRepository.observeActiveRoom(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing active room", rootError)
                    _uiState.value = MatchUiState.Error(
                        rootError.localizedMessage ?: "Connection mein masla aaya."
                    )
                }
                .collect { room ->
                    if (room != null && room.id.isNotBlank() && _uiState.value is MatchUiState.Searching) {
                        stopJobs()
                        _uiState.value = MatchUiState.Matched(room.id)
                    }
                }
        }
    }

    private fun startTimeoutCountdown(role: UserRole) {
        timerJob = viewModelScope.launch {
            var remaining = HelplineConfig.MATCH_TIMEOUT_SECONDS
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _uiState.update { current ->
                    if (current is MatchUiState.Searching) {
                        current.copy(secondsRemaining = remaining)
                    } else {
                        current
                    }
                }
            }
            if (_uiState.value is MatchUiState.Searching) {
                matchRepository.leaveQueue()
                observerJob?.cancel()
                _uiState.value = MatchUiState.TimedOut(role)
            }
        }
    }

    fun cancelMatching(onCancelled: () -> Unit = {}) {
        stopJobs()
        viewModelScope.launch {
            matchRepository.leaveQueue()
            _uiState.value = MatchUiState.Idle
            onCancelled()
        }
    }

    private fun stopJobs() {
        timerJob?.cancel()
        timerJob = null
        observerJob?.cancel()
        observerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopJobs()
    }

    private companion object {
        const val TAG = "MatchVM"
    }
}
