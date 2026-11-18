package com.example.data.repository

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ReportRepository(
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
            ?: throw IllegalStateException("User must be signed in before reporting.")

    suspend fun submitReport(
        roomId: String,
        reportedUid: String,
        reason: String,
        lastMessages: List<String>
    ): Result<Unit> {
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("reports").document(reportId)
        return try {
            val uid = requireUserId()
            val sanitizedReason = reason.trim().ifBlank { "Inappropriate behavior" }.take(200)
            val sanitizedMessages = lastMessages.takeLast(5).map { it.take(550) }

            docRef.set(
                mapOf(
                    "roomId" to roomId,
                    "reporterUid" to uid,
                    "reportedUid" to reportedUid,
                    "reason" to sanitizedReason,
                    "lastMessages" to sanitizedMessages,
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

    suspend fun blockPartner(partnerUid: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        val userRef = db.collection("users").document(uid)
        return try {
            userRef.update(
                mapOf(
                    "blockedUids" to FieldValue.arrayUnion(partnerUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }
}
