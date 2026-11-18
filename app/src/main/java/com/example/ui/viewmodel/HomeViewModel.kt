package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HomeUiState(
    val selectedRole: UserRole? = null,
    val bannerMessage: String? = null
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun selectRole(role: UserRole) {
        _uiState.update { it.copy(selectedRole = role) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedRole = null) }
    }

    fun showBanner(message: String) {
        _uiState.update { it.copy(bannerMessage = message) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }
}
