package com.quizduel.app.ui.result

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.quizduel.app.data.model.QuizResult
import com.quizduel.app.databinding.ItemResultQuestionBinding

class ResultAdapter(
    private val results: List<QuizResult>
) : RecyclerView.Adapter<ResultAdapter.ResultViewHolder>() {

    inner class ResultViewHolder(val binding: ItemResultQuestionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ItemResultQuestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val result = results[position]

        // Set the question text
        holder.binding.tvQuestionText.text = result.question

        // Strip the "A) " prefix from the User Answer
        val cleanUserAnswer = if (result.userAnswer.length > 3 && result.userAnswer[1] == ')') {
            result.userAnswer.substring(3)
        } else {
            result.userAnswer
        }
        holder.binding.tvUserAnswer.text = cleanUserAnswer

        // Strip the "A) " prefix from the Correct Answer
        val cleanCorrectAnswer = if (result.correctAnswer.length > 3 && result.correctAnswer[1] == ')') {
            result.correctAnswer.substring(3)
        } else {
            result.correctAnswer
        }
        holder.binding.tvCorrectAnswer.text = cleanCorrectAnswer

        // Apply visual styling based on if they got it right
        if (result.isCorrect) {
            // Correct - Hide the correct answer row (since they already guessed it!)
            holder.binding.layoutCorrectAnswer.visibility = View.GONE

            // Set Icon to Green Check
            holder.binding.cardResultIcon.setCardBackgroundColor(Color.parseColor("#D4EDDA"))
            holder.binding.tvResultIcon.text = "✓"
            holder.binding.tvResultIcon.setTextColor(Color.parseColor("#155724"))
        } else {
            // Wrong - Show the correct answer row so they can learn!
            holder.binding.layoutCorrectAnswer.visibility = View.VISIBLE

            // Set Icon to Red X
            holder.binding.cardResultIcon.setCardBackgroundColor(Color.parseColor("#F8D7DA"))
            holder.binding.tvResultIcon.text = "✕"
            holder.binding.tvResultIcon.setTextColor(Color.parseColor("#721C24"))
        }
    }

    override fun getItemCount() = results.size
}