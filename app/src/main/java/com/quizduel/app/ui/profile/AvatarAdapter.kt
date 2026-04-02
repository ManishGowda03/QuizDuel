package com.quizduel.app.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.quizduel.app.R
import coil.load
import coil.decode.SvgDecoder

class AvatarAdapter(
    private val avatars: List<AvatarUtils.Avatar>,
    private var selectedAvatarId: Int,
    private val onAvatarClick: (Int) -> Unit
) : RecyclerView.Adapter<AvatarAdapter.AvatarViewHolder>() {

    inner class AvatarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: CardView = itemView.findViewById(R.id.cardAvatar)
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatarItem)
        val tvName: TextView = itemView.findViewById(R.id.tvAvatarName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_avatar, parent, false)
        return AvatarViewHolder(view)
    }

    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        val avatar = avatars[position]

        holder.tvName.text = avatar.name

        holder.ivAvatar.load(AvatarUtils.getAvatarUrl(avatar.id)) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
        }

        // Apply Claymorphism Colors
        if (avatar.id == selectedAvatarId) {
            holder.card.setCardBackgroundColor(
                holder.itemView.context.getColor(R.color.clay_primary) // Solid Indigo
            )
            holder.tvName.setTextColor(
                holder.itemView.context.getColor(R.color.clay_white)   // White Text
            )
        } else {
            holder.card.setCardBackgroundColor(
                holder.itemView.context.getColor(R.color.clay_white)   // White Card
            )
            holder.tvName.setTextColor(
                holder.itemView.context.getColor(R.color.clay_text_muted) // Muted Text
            )
        }

        holder.card.setOnClickListener {
            val prev = selectedAvatarId
            selectedAvatarId = avatar.id
            notifyItemChanged(avatars.indexOfFirst { it.id == prev })
            notifyItemChanged(position)
            onAvatarClick(avatar.id)
        }
    }

    override fun getItemCount() = avatars.size
}