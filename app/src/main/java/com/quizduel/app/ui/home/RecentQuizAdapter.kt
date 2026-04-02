package com.quizduel.app.ui.home

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.quizduel.app.R
import com.quizduel.app.data.model.Topic
import com.quizduel.app.databinding.ItemRecentQuizBinding

class RecentQuizAdapter(
    private var quizzes: List<Topic>,
    private val onQuizClick: (Topic) -> Unit
) : RecyclerView.Adapter<RecentQuizAdapter.QuizViewHolder>() {

    inner class QuizViewHolder(val binding: ItemRecentQuizBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuizViewHolder {
        val binding = ItemRecentQuizBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QuizViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuizViewHolder, position: Int) {
        val quiz = quizzes[position]
        val context = holder.itemView.context

        holder.binding.tvTopicName.text = quiz.name
        holder.binding.tvQuestionCount.text = "${quiz.questionCount} Questions"
        // Set text to ALL CAPS like the design
        holder.binding.tvDifficulty.text = quiz.difficulty.uppercase()

        // Match the exact light background / dark text from the image
        val bgColor: Int
        val textColor: Int

        when (quiz.difficulty.lowercase()) {
            "easy" -> {
                bgColor = android.graphics.Color.parseColor("#E8EAFF") // Light Indigo
                textColor = android.graphics.Color.parseColor("#5D5FEF") // Solid Indigo
            }
            "medium" -> {
                bgColor = android.graphics.Color.parseColor("#F2F2F2") // Light Gray
                textColor = android.graphics.Color.parseColor("#757575") // Dark Gray
            }
            "hard" -> {
                bgColor = android.graphics.Color.parseColor("#FFE8EE") // Light Pink/Red
                textColor = android.graphics.Color.parseColor("#FF4B4B") // Solid Red
            }
            else -> {
                bgColor = android.graphics.Color.parseColor("#E8EAFF")
                textColor = android.graphics.Color.parseColor("#5D5FEF")
            }
        }

        // Apply Background
        holder.binding.tvDifficulty.backgroundTintList =
            android.content.res.ColorStateList.valueOf(bgColor)

        // Apply Text Color
        holder.binding.tvDifficulty.setTextColor(textColor)
        // Decode Base64 Image
        if (quiz.imageUrl.isNotEmpty()) {
            try {
                val decodedString = Base64.decode(quiz.imageUrl, Base64.DEFAULT)
                val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                holder.binding.ivTopicImage.setImageBitmap(decodedByte)
            } catch (e: Exception) {
                holder.binding.ivTopicImage.setBackgroundColor(context.getColor(R.color.clay_bg))
            }
        } else {
            holder.binding.ivTopicImage.setBackgroundColor(context.getColor(R.color.clay_bg))
        }

        holder.binding.btnPlay.setOnClickListener { onQuizClick(quiz) }
    }

    override fun getItemCount() = quizzes.size

    fun updateList(newList: List<Topic>) {
        quizzes = newList
        notifyDataSetChanged()
    }
}