package com.quizduel.app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.quizduel.app.data.model.Question
import com.quizduel.app.databinding.ItemAdminQuestionBinding

class AdminQuestionAdapter(
    private val questions: List<Question>,
    private val onEdit: (Question) -> Unit,
    private val onDelete: (Question) -> Unit
) : RecyclerView.Adapter<AdminQuestionAdapter.QuestionViewHolder>() {

    inner class QuestionViewHolder(val binding: ItemAdminQuestionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
        val binding = ItemAdminQuestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return QuestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
        val question = questions[position]

        holder.binding.tvQuestionNumber.text = "Q${position + 1}"
        holder.binding.tvQuestionText.text = question.question
        holder.binding.tvOptionA.text = question.optionA
        holder.binding.tvOptionB.text = question.optionB
        holder.binding.tvOptionC.text = question.optionC
        holder.binding.tvOptionD.text = question.optionD
        holder.binding.tvCorrectAnswer.text = question.correctAnswer

        // Difficulty code completely removed here

        holder.binding.btnEdit.setOnClickListener { onEdit(question) }
        holder.binding.btnDelete.setOnClickListener { onDelete(question) }
    }

    override fun getItemCount() = questions.size
}