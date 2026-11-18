package com.example.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.model.MoodChoice
import com.example.data.model.UserRole
import com.example.data.preferences.UserPreferencesStore
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.MatchRepository
import com.example.data.repository.ReportRepository
import com.example.ui.screens.AgeGateScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MatchingScreen
import com.example.ui.screens.RulesScreen
import com.example.ui.screens.SessionEndScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.MatchViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.google.firebase.firestore.FirebaseFirestore

object Routes {
    const val HOME = "home"
    const val MATCHING = "matching/{role}"
    const val CHAT = "chat/{roomId}"
    const val SESSION_END = "session_end"
    const val SETTINGS = "settings"

    fun matching(role: UserRole): String = "matching/${role.name}"
    fun chat(roomId: String): String = "chat/$roomId"
}

@Composable
fun KhulKeBoloRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val prefs = UserPreferencesStore(app)
                AuthViewModel(AuthRepository(db, prefs), prefs)
            }
        }
    )

    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val onboarding by authViewModel.onboardingState.collectAsStateWithLifecycle()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        authViewModel.initializeApp(context)
    }

    // Strict auth and onboarding gating: Firestore feature screens and ViewModels
    // only compose after Splash -> AgeGate -> Rules -> Authenticated User.
    when {
        !authUiState.splashFinished -> {
            SplashScreen()
        }

        !onboarding.ageConfirmed -> {
            AgeGateScreen(
                ageRejected = onboarding.ageRejected,
                onConfirm18Plus = { authViewModel.confirmAge(true) },
                onUnder18Selected = { authViewModel.confirmAge(false) }
            )
        }

        !onboarding.rulesAccepted -> {
            RulesScreen(
                onAcceptRules = { authViewModel.acceptRules() }
            )
        }

        currentUser == null -> {
            AuthScreen(
                isSigningIn = authUiState.isSigningIn,
                errorMessage = authUiState.errorMessage,
                onGoogleSignInClick = { activity -> authViewModel.signInWithGoogle(activity) }
            )
        }

        else -> {
            val uid = currentUser!!.uid
            key(uid) {
                AuthenticatedAppNavHost(currentUserId = uid)
            }
        }
    }
}

@Composable
private fun AuthenticatedAppNavHost(currentUserId: String) {
    val navController = rememberNavController()
    var lastEndedNotice by remember { mutableStateOf<String?>(null) }
    var lastCrisisFlag by remember { mutableStateOf(false) }
    var lastMoodSubmitted by remember { mutableStateOf(false) }
    var lastSelectedMood by remember { mutableStateOf<MoodChoice?>(null) }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartRole = { role ->
                    navController.navigate(Routes.matching(role))
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = Routes.MATCHING,
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { backStackEntry ->
            val role = UserRole.fromRaw(backStackEntry.arguments?.getString("role"))
            val matchViewModel: MatchViewModel = viewModel(
                key = "${currentUserId}_match_${role.name}",
                factory = viewModelFactory {
                    initializer {
                        val application = checkNotNull(this[APPLICATION_KEY])
                        val db = FirebaseFirestore.getInstance(
                            application.getString(R.string.firestore_database_id)
                        )
                        MatchViewModel(MatchRepository(db), currentUserId)
                    }
                }
            )
            val matchState by matchViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(role) {
                matchViewModel.startMatching(role)
            }

            MatchingScreen(
                role = role,
                uiState = matchState,
                onRetry = { matchViewModel.startMatching(role) },
                onCancel = {
                    matchViewModel.cancelMatching {
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    }
                },
                onMatched = { roomId ->
                    navController.navigate(Routes.chat(roomId)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId").orEmpty()
            val chatViewModel: ChatViewModel = viewModel(
                key = "${currentUserId}_chat_$roomId",
                factory = viewModelFactory {
                    initializer {
                        val application = checkNotNull(this[APPLICATION_KEY])
                        val db = FirebaseFirestore.getInstance(
                            application.getString(R.string.firestore_database_id)
                        )
                        ChatViewModel(
                            chatRepository = ChatRepository(db),
                            reportRepository = ReportRepository(db),
                            roomId = roomId,
                            currentUserId = currentUserId
                        )
                    }
                }
            )
            val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()

            ChatScreen(
                currentUserId = currentUserId,
                uiState = chatState,
                onInputChanged = chatViewModel::onInputTextChanged,
                onSendClick = chatViewModel::sendMessage,
                onQuickPhraseClick = chatViewModel::sendQuickPhrase,
                onRequestExtension = chatViewModel::requestExtension,
                onEndSession = { chatViewModel.endSessionEarly() },
                onReportSubmit = chatViewModel::submitReport,
                onBlockPartner = chatViewModel::blockPartnerAndEnd,
                onClearFeedback = chatViewModel::clearFeedback,
                onNavigateToEndScreen = {
                    lastEndedNotice = chatState.endedNotice
                    lastCrisisFlag = chatState.crisisDetected
                    lastMoodSubmitted = false
                    lastSelectedMood = null
                    navController.navigate(Routes.SESSION_END) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                }
            )
        }

        composable(Routes.SESSION_END) {
            val endViewModel: ChatViewModel? = null
            // Use lightweight ChatRepository call directly inside Settings/End helper via a dedicated state holder
            val context = LocalContext.current
            val chatRepo = remember(currentUserId) { ChatRepository(context) }

            var submitted by remember { mutableStateOf(lastMoodSubmitted) }
            var selected by remember { mutableStateOf(lastSelectedMood) }
            var triggerSubmit by remember { mutableStateOf<MoodChoice?>(null) }

            LaunchedEffect(triggerSubmit) {
                val mood = triggerSubmit ?: return@LaunchedEffect
                chatRepo.submitMoodFeedback(mood).onSuccess {
                    submitted = true
                    lastMoodSubmitted = true
                }
                triggerSubmit = null
            }

            SessionEndScreen(
                endedNotice = lastEndedNotice,
                selectedMood = selected,
                moodSubmitted = submitted,
                crisisDetectedEarlier = lastCrisisFlag,
                onSelectMood = { mood ->
                    if (!submitted) {
                        selected = mood
                        lastSelectedMood = mood
                        triggerSubmit = mood
                    }
                },
                onDoneToHome = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        composable(Routes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(
                key = "${currentUserId}_settings",
                factory = viewModelFactory {
                    initializer {
                        val application = checkNotNull(this[APPLICATION_KEY])
                        val db = FirebaseFirestore.getInstance(
                            application.getString(R.string.firestore_database_id)
                        )
                        val prefs = UserPreferencesStore(application)
                        SettingsViewModel(AuthRepository(db, prefs))
                    }
                }
            )
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            SettingsScreen(
                uiState = settingsState,
                onBack = { navController.popBackStack() },
                onDeleteMyData = { ctx ->
                    settingsViewModel.deleteAllData(ctx) {
                        // AuthStateListener & DataStore clear will route back cleanly
                    }
                },
                onSignOut = { ctx ->
                    settingsViewModel.signOut(ctx)
                },
                onClearMessages = settingsViewModel::clearMessages
            )
        }
    }
}
