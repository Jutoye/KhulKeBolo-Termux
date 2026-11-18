package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.config.HelplineConfig
import com.example.data.model.ChatRoom
import com.example.data.model.QueueEntry
import com.example.data.model.RoomStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import kotlin.random.Random

class MatchRepository(
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
            ?: throw IllegalStateException("User must be signed in before matching.")

    fun generateRandomNickname(): String {
        val number = Random.nextInt(1000, 10000)
        return "Dost #$number"
    }

    /**
     * Checks if user has 3 or more reports against them within the past 24 hours.
     */
    suspend fun isTemporarilyBanned(userId: String): Result<Boolean> {
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val snapshot = db.collection("reports")
                .whereEqualTo("reportedUid", uid)
                .get()
                .await()

            val cutoffMillis = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
            val recentCount = snapshot.documents.count { doc ->
                val ts = doc.getTimestamp("createdAt")
                ts != null && ts.toDate().time >= cutoffMillis
            }
            Result.success(recentCount >= 3)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "reports")
            Result.failure(e)
        }
    }

    /**
     * Enters the matchmaking queue and attempts to pair immediately with a waiting partner
     * inside an atomic Firestore transaction.
     */
    suspend fun enterQueueAndTryMatch(role: UserRole): Result<String?> {
        val myUid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))

        return try {
            // 1. Verify 24h report ban threshold
            val bannedResult = isTemporarilyBanned(myUid)
            if (bannedResult.getOrDefault(false)) {
                return Result.failure(
                    IllegalStateException(
                        "Aap ke account par pichle 24 ghanton mein 3 ya zyada reports hain. 24 ghante baad dubara koshish karein."
                    )
                )
            }

            // 2. Load my blockedUids list
            val userSnap = db.collection("users").document(myUid).get().await()
            val myBlocked = userSnap.toObject(UserProfile::class.java)?.blockedUids ?: emptyList()
            val myNickname = generateRandomNickname()

            // 3. Upsert queue entry for myUid
            val queueRef = db.collection("queue").document(myUid)
            val queuePayload = mapOf(
                "uid" to myUid,
                "role" to role.name,
                "nickname" to myNickname,
                "blockedUids" to myBlocked,
                "createdAt" to FieldValue.serverTimestamp()
            )
            queueRef.set(queuePayload).await()

            // 4. Query waiting candidates of opposite role
            val oppositeRole = role.opposite()
            val candidatesSnap = db.collection("queue")
                .whereEqualTo("role", oppositeRole.name)
                .limit(20)
                .get()
                .await()

            val candidates = candidatesSnap.documents
                .mapNotNull { it.toObject(QueueEntry::class.java) }
                .filter { candidate ->
                    candidate.uid.isNotBlank() &&
                        candidate.uid != myUid &&
                        candidate.uid !in myBlocked &&
                        myUid !in candidate.blockedUids
                }

            for (candidate in candidates) {
                val roomId = attemptMatchTransaction(
                    myUid = myUid,
                    myRole = role,
                    myNickname = myNickname,
                    partner = candidate
                )
                if (roomId != null) {
                    return Result.success(roomId)
                }
            }

            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "queue/$myUid")
            Result.failure(e)
        }
    }

    private suspend fun attemptMatchTransaction(
        myUid: String,
        myRole: UserRole,
        myNickname: String,
        partner: QueueEntry
    ): String? {
        val myQueueRef = db.collection("queue").document(myUid)
        val partnerQueueRef = db.collection("queue").document(partner.uid)
        val newRoomId = "room_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val roomRef = db.collection("rooms").document(newRoomId)

        return try {
            db.runTransaction { tx ->
                val mySnap = tx.get(myQueueRef)
                val partnerSnap = tx.get(partnerQueueRef)
                if (!mySnap.exists() || !partnerSnap.exists()) {
                    return@runTransaction null
                }

                val speakerUid = if (myRole == UserRole.SPEAKER) myUid else partner.uid
                val listenerUid = if (myRole == UserRole.LISTENER) myUid else partner.uid
                val speakerNick = if (myRole == UserRole.SPEAKER) myNickname else partner.nickname
                val listenerNick = if (myRole == UserRole.LISTENER) myNickname else partner.nickname

                val nowMillis = System.currentTimeMillis()
                val endsAtDate = Date(nowMillis + HelplineConfig.DEFAULT_SESSION_MINUTES * 60_000L)
                val deleteAtDate = Date(
                    endsAtDate.time + HelplineConfig.MESSAGE_TTL_EXTRA_MINUTES * 60_000L
                )

                val roomPayload = mapOf(
                    "speakerUid" to speakerUid,
                    "listenerUid" to listenerUid,
                    "participantUids" to listOf(speakerUid, listenerUid),
                    "speakerNickname" to speakerNick,
                    "listenerNickname" to listenerNick,
                    "status" to RoomStatus.ACTIVE.name,
                    "startedAt" to FieldValue.serverTimestamp(),
                    "endsAt" to Timestamp(endsAtDate),
                    "deleteAt" to Timestamp(deleteAtDate),
                    "extendRequestedBySpeaker" to false,
                    "extendRequestedByListener" to false,
                    "extendedApplied" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                tx.set(roomRef, roomPayload)
                tx.delete(myQueueRef)
                tx.delete(partnerQueueRef)
                newRoomId
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Observes active rooms where the current user is a participant.
     */
    fun observeActiveRoom(userId: String): Flow<ChatRoom?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val query = db.collection("rooms")
            .whereArrayContains("participantUids", userId)

        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(
                                ChatRoom::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        }
                        .filter { !it.isEnded() }
                        .maxByOrNull { it.startedAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "rooms")
                    }
                    throw error
                }
        )
    }

    suspend fun leaveQueue(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val docRef = db.collection("queue").document(uid)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
