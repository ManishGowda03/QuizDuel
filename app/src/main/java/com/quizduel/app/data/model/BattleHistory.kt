package com.quizduel.app.data.model

data class BattleHistory(
    val battleId: String = "",
    val opponentName: String = "",
    val opponentUid: String = "",
    val opponentAvatarId: Int = 1,
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val result: String = "draw",   // "win" / "loss" / "draw"
    val topicName: String = "",
    val questionCount: Int = 0,
    val timestamp: Long = 0L,
    val questions: List<BattleHistoryQuestion> = emptyList()
)

data class BattleHistoryQuestion(
    val questionText: String = "",
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val correctAnswer: String = "",
    val myAnswer: String = "",
    val opponentAnswer: String = ""
)