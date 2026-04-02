package com.quizduel.app.data.model

data class Topic(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val imageUrl: String = "",
    val questionCount: Int = 0,
    val difficulty: String = "",
    @get:JvmName("getIsActive")
    val isActive: Boolean = true,
    val icon: String = ""
)