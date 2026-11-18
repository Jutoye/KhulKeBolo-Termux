package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val speakerUid: String = "",
    val listenerUid: String = "",
    val participantUids: List<String> = emptyList(),
    val speakerNickname: String = "",
    val listenerNickname: String = "",
    val status: String = RoomStatus.ACTIVE.name,
    @ServerTimestamp val startedAt: Timestamp? = null,
    val endsAt: Timestamp? = null,
    val deleteAt: Timestamp? = null,
    val extendRequestedBySpeaker: Boolean = false,
    val extendRequestedByListener: Boolean = false,
    val extendedApplied: Boolean = false,
    val endedByUid: String? = null,
    val endedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleFor(uid: String): UserRole =
        if (uid == speakerUid) UserRole.SPEAKER else UserRole.LISTENER

    fun partnerUidFor(uid: String): String =
        if (uid == speakerUid) listenerUid else speakerUid

    fun partnerNicknameFor(uid: String): String =
        if (uid == speakerUid) listenerNickname else speakerNickname

    fun myNicknameFor(uid: String): String =
        if (uid == speakerUid) speakerNickname else listenerNickname

    fun isEnded(): Boolean = RoomStatus.fromRaw(status) == RoomStatus.ENDED
}
