package com.quizduel.app.data.model

data class QuizResult(
    val question: String = "",
    val userAnswer: String = "",
    val correctAnswer: String = "",
    val isCorrect: Boolean = false,
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = ""
)