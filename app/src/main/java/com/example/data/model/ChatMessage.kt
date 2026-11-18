package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderUid: String = "",
    val senderRole: String = UserRole.SPEAKER.name,
    val participantUids: List<String> = emptyList(),
    val text: String = "",
    val flagged: Boolean = false,
    val flagReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)
