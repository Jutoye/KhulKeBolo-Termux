package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.model.UserProfile
import com.example.data.preferences.UserPreferencesStore
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepository(
    private val db: FirebaseFirestore,
    private val prefsStore: UserPreferencesStore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        prefsStore = UserPreferencesStore(context.applicationContext)
    )

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun attemptSilentSignIn(context: Context): Boolean {
        if (auth.currentUser != null) return true
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return false
        }
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun signInWithGoogle(activity: Activity): Result<Unit> {
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return Result.failure(IllegalStateException("Google Sign-In configuration missing."))
        }
        return try {
            val credentialManager = CredentialManager.create(activity)
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type returned."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            Result.failure(SignInCancelledException())
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun syncConsentToFirestore(ageConfirmed: Boolean, rulesAccepted: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val docRef = db.collection("users").document(uid)
        return try {
            if (!ageConfirmed) return Result.success(Unit)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "uid" to uid,
                    "ageConfirmed" to true,
                    "blockedUids" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (rulesAccepted) {
                    payload["rulesAcceptedAt"] = FieldValue.serverTimestamp()
                }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "ageConfirmed" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (rulesAccepted) {
                    updates["rulesAcceptedAt"] = FieldValue.serverTimestamp()
                }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    suspend fun deleteAllUserData(context: Context): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("User is not signed in."))
        val uid = user.uid
        return withContext(NonCancellable) {
            try {
                // 1. Delete waiting queue entry if any
                val queueRef = db.collection("queue").document(uid)
                runCatching { queueRef.delete().await() }

                // 2. Delete reports created by this user
                val reportsSnapshot = db.collection("reports")
                    .whereEqualTo("reporterUid", uid)
                    .get()
                    .await()
                if (!reportsSnapshot.isEmpty) {
                    val batch = db.batch()
                    reportsSnapshot.documents.forEach { doc -> batch.delete(doc.reference) }
                    batch.commit().await()
                }

                // 3. Delete user document
                db.collection("users").document(uid).delete().await()

                // 4. Clear local DataStore flags
                prefsStore.clearAll()

                // 5. Clear credential state and delete auth account (fallback to signOut if re-auth required)
                try {
                    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                } catch (_: Exception) {
                }

                try {
                    user.delete().await()
                } catch (_: Exception) {
                    auth.signOut()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "users/$uid")
                Result.failure(e)
            }
        }
    }

    class SignInCancelledException : Exception("Sign-in cancelled by user")

    private companion object {
        const val TAG = "AuthRepository"
    }
}
