package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun deleteAllData(context: Context, onDeleted: () -> Unit) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, statusMessage = null, errorMessage = null) }
            val result = authRepository.deleteAllUserData(context)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "Aap ka sara data delete kar diya gaya hai."
                    )
                }
                onDeleted()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = error.localizedMessage ?: "Data delete karne mein masla aaya."
                    )
                }
            }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authRepository.signOut(context)
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
