package com.quizduel.app.ui.duel

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.decode.SvgDecoder
import com.quizduel.app.R
import com.quizduel.app.data.model.Topic

class BattleTopicAdapter(
    private val topics: List<Topic>,
    private val onTopicClick: (Topic) -> Unit
) : RecyclerView.Adapter<BattleTopicAdapter.TopicViewHolder>() {

    private var selectedPosition = -1

    inner class TopicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: CardView = itemView.findViewById(R.id.cardView)
        val ivImage: ImageView = itemView.findViewById(R.id.ivTopicImage)
        val tvName: TextView = itemView.findViewById(R.id.tvTopicName)
        val tvCount: TextView = itemView.findViewById(R.id.tvQuestionCount)
        val btnPlay: View = itemView.findViewById(R.id.btnPlay)
        val tvDifficulty: TextView = itemView.findViewById(R.id.tvDifficulty)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_quiz, parent, false)
        return TopicViewHolder(view)
    }

    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        val topic = topics[position]
        holder.tvName.text = topic.name
        holder.tvCount.text = "${topic.questionCount} Questions"

        // Hide these for the compact picker view
        holder.btnPlay.visibility = View.GONE
        holder.tvDifficulty.visibility = View.GONE

        // --- IMAGE LOADING LOGIC ---
        val imageUrl = topic.imageUrl
        if (imageUrl.isNotEmpty()) {
            // Check if it's the Base64 hack (starts with data:image or is just a massive string)
            if (imageUrl.startsWith("data:image") || (!imageUrl.startsWith("http") && imageUrl.length > 100)) {
                try {
                    // Clean the string if it has the data:image prefix
                    val cleanBase64 = if (imageUrl.contains(",")) imageUrl.substringAfter(",") else imageUrl
                    // Decode the Base64 string into raw bytes
                    val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    // Convert bytes to a Bitmap
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    holder.ivImage.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    // If decoding fails, safely ignore it so the app doesn't crash
                    e.printStackTrace()
                }
            } else {
                // If it's a normal web URL, let Coil handle it (including SVGs)
                holder.ivImage.load(imageUrl) {
                    decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
                    crossfade(true)
                }
            }
        } else {
            // Clear the image if there is no URL
            holder.ivImage.setImageDrawable(null)
        }

        // Apply Claymorphism Selection Colors
        if (position == selectedPosition) {
            // Selected: Indigo background, White text
            holder.card.setCardBackgroundColor(
                holder.itemView.context.getColor(R.color.clay_primary)
            )
            holder.tvName.setTextColor(
                holder.itemView.context.getColor(R.color.clay_white)
            )
            holder.tvCount.setTextColor(
                holder.itemView.context.getColor(R.color.clay_white)
            )
        } else {
            // Unselected: White background, Dark text
            holder.card.setCardBackgroundColor(
                holder.itemView.context.getColor(R.color.clay_white)
            )
            holder.tvName.setTextColor(
                holder.itemView.context.getColor(R.color.clay_text_dark)
            )
            holder.tvCount.setTextColor(
                holder.itemView.context.getColor(R.color.clay_text_muted)
            )
        }

        holder.card.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onTopicClick(topic)
        }
    }

    override fun getItemCount() = topics.size
}