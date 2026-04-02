package com.quizduel.app.ui.admin

import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.quizduel.app.R
import com.quizduel.app.data.model.Topic
import com.quizduel.app.databinding.ItemAdminTopicBinding

class AdminTopicAdapter(
    private val topics: List<Topic>,
    private val onEdit: (Topic) -> Unit,
    private val onDelete: (Topic) -> Unit
) : RecyclerView.Adapter<AdminTopicAdapter.TopicViewHolder>() {

    inner class TopicViewHolder(val binding: ItemAdminTopicBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder {
        val binding = ItemAdminTopicBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TopicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        val topic = topics[position]

        holder.binding.tvTopicName.text = topic.name
        holder.binding.tvTopicCategory.text = topic.category
        holder.binding.tvQuestionCount.text = "${topic.questionCount} questions"
        holder.binding.tvDifficulty.text = topic.difficulty

        // Set difficulty badge color
        val badgeColor = when (topic.difficulty.lowercase()) {
            "easy" -> R.color.difficulty_easy
            "medium" -> R.color.difficulty_medium
            "hard" -> R.color.difficulty_hard
            else -> R.color.brand_red
        }
        holder.binding.tvDifficulty.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                holder.itemView.context.getColor(badgeColor)
            )

        // IMAGE DECODING LOGIC
        if (topic.imageUrl.isNotEmpty()) {
            // Hide the emoji, show the image view
            holder.binding.tvTopicIcon.visibility = View.GONE
            holder.binding.ivTopicImage.visibility = View.VISIBLE

            try {
                // Convert the massive text string back into a picture
                val imageBytes = Base64.decode(topic.imageUrl, Base64.DEFAULT)
                Glide.with(holder.itemView.context)
                    .load(imageBytes)
                    .centerCrop()
                    .into(holder.binding.ivTopicImage)
            } catch (e: Exception) {
                // Fallback just in case something goes wrong
                Glide.with(holder.itemView.context)
                    .load(topic.imageUrl)
                    .centerCrop()
                    .into(holder.binding.ivTopicImage)
            }
        } else {
            // No image found, fall back to the emoji!
            holder.binding.ivTopicImage.visibility = View.GONE
            holder.binding.tvTopicIcon.visibility = View.VISIBLE
            holder.binding.tvTopicIcon.text = topic.icon.ifEmpty { "💻" }
        }

        holder.binding.btnEdit.setOnClickListener { onEdit(topic) }
        holder.binding.btnDelete.setOnClickListener { onDelete(topic) }
    }

    override fun getItemCount() = topics.size
}