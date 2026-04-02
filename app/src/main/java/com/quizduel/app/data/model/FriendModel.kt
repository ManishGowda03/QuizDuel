package com.quizduel.app.data.model

data class FriendModel(
    val uid: String = "",
    val username: String = "",
    val avatarId: Int = 1,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L
)

data class FriendRequest(
    val uid: String = "",
    val username: String = "",
    val avatarId: Int = 1,
    val timestamp: Long = 0L
)

data class BattleInvite(
    val inviteId: String = "",
    val fromUid: String = "",
    val fromUsername: String = "",
    val fromAvatarId: Int = 1,
    val roomCode: String = "",
    val topicName: String = "",
    val topicId: String = "",
    val questionCount: Int = 10,
    val status: String = "pending",  // pending / accepted / rejected
    val timestamp: Long = 0L
)