package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.config.HelplineConfig
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.MoodChoice
import com.example.data.model.RoomStatus
import com.example.data.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.snapshots
import java.util.Date
import java.util.UUID

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing chat.")

    fun observeRoom(roomId: String): Flow<ChatRoom?> = flow {
        val uid = requireUserId()
        require(roomId.isNotBlank()) { "Room ID must not be blank" }
        val docRef = db.collection("rooms").document(roomId)

        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            ChatRoom::class.java,
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
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(roomId: String, currentUserId: String): Flow<List<ChatMessage>> = flow {
        require(roomId.isNotBlank() && currentUserId.isNotBlank()) {
            "Room ID and User ID must not be blank"
        }
        val query = db.collection("rooms")
            .document(roomId)
            .collection("messages")
            .whereArrayContains("participantUids", currentUserId)

        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(
                                ChatMessage::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        }
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(
                            exceptionToLog,
                            OperationType.LIST,
                            "rooms/$roomId/messages"
                        )
                    }
                    throw error
                }
        )
    }

    suspend fun sendMessage(
        room: ChatRoom,
        text: String,
        flagged: Boolean,
        flagReason: String?
    ): Result<Unit> {
        val docPath = "rooms/${room.id}/messages"
        return try {
            val uid = requireUserId()
            val trimmed = text.trim()
            require(trimmed.isNotEmpty() && trimmed.length <= HelplineConfig.MAX_MESSAGE_LENGTH) {
                "Message must be between 1 and ${HelplineConfig.MAX_MESSAGE_LENGTH} characters."
            }
            val msgRef = db.collection("rooms")
                .document(room.id)
                .collection("messages")
                .document()

            val payload = mutableMapOf<String, Any>(
                "roomId" to room.id,
                "senderUid" to uid,
                "senderRole" to room.roleFor(uid).name,
                "participantUids" to room.participantUids,
                "text" to trimmed,
                "flagged" to flagged,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!flagReason.isNullOrBlank()) {
                payload["flagReason"] = flagReason.take(120)
            }
            msgRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun requestOrConfirmExtension(roomId: String, myRole: UserRole): Result<Unit> {
        val docRef = db.collection("rooms").document(roomId)
        return try {
            requireUserId()
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val room = snap.toObject(ChatRoom::class.java)
                    ?: throw IllegalStateException("Room not found")

                if (room.isEnded() || room.extendedApplied) return@runTransaction null

                val speakerAgreed = room.extendRequestedBySpeaker || (myRole == UserRole.SPEAKER)
                val listenerAgreed = room.extendRequestedByListener || (myRole == UserRole.LISTENER)

                val updates = mutableMapOf<String, Any>(
                    "extendRequestedBySpeaker" to speakerAgreed,
                    "extendRequestedByListener" to listenerAgreed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                if (speakerAgreed && listenerAgreed) {
                    val baseEndsAtMillis = room.endsAt?.toDate()?.time
                        ?: (System.currentTimeMillis() + HelplineConfig.DEFAULT_SESSION_MINUTES * 60_000L)
                    val newEndsAt = Date(baseEndsAtMillis + HelplineConfig.EXTENSION_MINUTES * 60_000L)
                    val newDeleteAt = Date(
                        newEndsAt.time + HelplineConfig.MESSAGE_TTL_EXTRA_MINUTES * 60_000L
                    )
                    updates["extendedApplied"] = true
                    updates["endsAt"] = Timestamp(newEndsAt)
                    updates["deleteAt"] = Timestamp(newDeleteAt)
                }

                tx.update(docRef, updates)
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun endSessionAndDeleteMessages(roomId: String): Result<Unit> {
        val roomRef = db.collection("rooms").document(roomId)
        return try {
            val uid = requireUserId()
            val snap = roomRef.get().await()
            val room = snap.toObject(ChatRoom::class.java)

            if (room != null && !room.isEnded()) {
                val now = System.currentTimeMillis()
                val deleteAt = Date(now + HelplineConfig.MESSAGE_TTL_EXTRA_MINUTES * 60_000L)
                roomRef.update(
                    mapOf(
                        "status" to RoomStatus.ENDED.name,
                        "endedByUid" to uid,
                        "endedAt" to FieldValue.serverTimestamp(),
                        "deleteAt" to Timestamp(deleteAt),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Delete ephemeral messages immediately upon session end
            deleteMessagesForRoom(roomId, uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessagesForRoom(roomId: String, uid: String) {
        try {
            val messagesSnap = db.collection("rooms")
                .document(roomId)
                .collection("messages")
                .whereArrayContains("participantUids", uid)
                .get()
                .await()

            if (!messagesSnap.isEmpty) {
                val batch = db.batch()
                messagesSnap.documents.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "rooms/$roomId/messages")
        }
    }

    suspend fun logCrisisEvent(roomId: String): Result<Unit> {
        val docId = "crisis_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("crisis_events").document(docId)
        return try {
            requireUserId()
            docRef.set(
                mapOf(
                    "roomId" to roomId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitMoodFeedback(mood: MoodChoice): Result<Unit> {
        val docId = "mood_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("mood_feedback").document(docId)
        return try {
            requireUserId()
            docRef.set(
                mapOf(
                    "mood" to mood.name,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }
}
