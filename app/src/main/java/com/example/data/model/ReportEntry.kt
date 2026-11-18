package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class ReportEntry(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val reporterUid: String = "",
    val reportedUid: String = "",
    val reason: String = "",
    val lastMessages: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
