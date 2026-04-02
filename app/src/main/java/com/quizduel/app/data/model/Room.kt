package com.quizduel.app.data.model

data class Room(
    val roomCode: String = "",
    val topicId: String = "",
    val topicName: String = "",
    val questionCount: Int = 10,
    val status: String = "waiting",   // waiting | ready | ongoing | finished
    val createdAt: Long = 0L,
    val players: Map<String, RoomPlayer> = emptyMap()
)

data class RoomPlayer(
    val uid: String = "",
    val username: String = "",
    val score: Int = 0,
    val answered: Int = 0,
    val ready: Boolean = false
)
