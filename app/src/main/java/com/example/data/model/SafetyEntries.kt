package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CrisisEvent(
    @DocumentId val id: String = "",
    val roomId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MoodEntry(
    @DocumentId val id: String = "",
    val mood: String = MoodChoice.WAISA_HI.name,
    @ServerTimestamp val createdAt: Timestamp? = null
)
