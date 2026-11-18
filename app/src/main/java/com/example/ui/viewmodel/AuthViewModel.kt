package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.LocalOnboardingState
import com.example.data.preferences.UserPreferencesStore
import com.example.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val splashFinished: Boolean = false,
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val prefsStore: UserPreferencesStore
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = authRepository.authStateFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = authRepository.currentUser
        )

    val onboardingState: StateFlow<LocalOnboardingState> = prefsStore.onboardingState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = LocalOnboardingState()
        )

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun initializeApp(context: Context) {
        if (_uiState.value.splashFinished) return
        viewModelScope.launch {
            authRepository.attemptSilentSignIn(context)
            delay(900L)
            _uiState.update { it.copy(splashFinished = true) }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        if (_uiState.value.isSigningIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningIn = true, errorMessage = null) }
            val result = authRepository.signInWithGoogle(activity)
            result.onSuccess {
                val local = onboardingState.value
                if (local.ageConfirmed) {
                    authRepository.syncConsentToFirestore(
                        ageConfirmed = true,
                        rulesAccepted = local.rulesAccepted
                    )
                }
                _uiState.update { it.copy(isSigningIn = false, errorMessage = null) }
            }.onFailure { error ->
                val message = if (error is AuthRepository.SignInCancelledException) {
                    null
                } else {
                    error.localizedMessage ?: "Sign in nahi ho saka. Dobara koshish karein."
                }
                _uiState.update { it.copy(isSigningIn = false, errorMessage = message) }
            }
        }
    }

    fun confirmAge(isEighteenOrOlder: Boolean) {
        viewModelScope.launch {
            prefsStore.setAgeConfirmed(isEighteenOrOlder)
            if (isEighteenOrOlder) {
                authRepository.syncConsentToFirestore(
                    ageConfirmed = true,
                    rulesAccepted = onboardingState.value.rulesAccepted
                )
            }
        }
    }

    fun acceptRules() {
        viewModelScope.launch {
            prefsStore.setRulesAccepted(System.currentTimeMillis())
            authRepository.syncConsentToFirestore(
                ageConfirmed = true,
                rulesAccepted = true
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
