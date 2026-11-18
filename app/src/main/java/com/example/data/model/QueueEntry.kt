package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class QueueEntry(
    @DocumentId val id: String = "",
    val uid: String = "",
    val role: String = UserRole.SPEAKER.name,
    val nickname: String = "",
    val blockedUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
