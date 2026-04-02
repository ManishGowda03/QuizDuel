package com.quizduel.app.ui.friends

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.decode.SvgDecoder
import coil.transform.CircleCropTransformation
import com.quizduel.app.R
import com.quizduel.app.data.model.FriendModel
import com.quizduel.app.ui.profile.AvatarUtils

class FriendsAdapter(
    private val friends: List<FriendModel>,
    private val onInviteClick: (FriendModel) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivFriendAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvFriendName)
        val tvStatus: TextView = itemView.findViewById(R.id.tvOnlineStatus)
        val onlineDot: View = itemView.findViewById(R.id.onlineDot)
        val btnInvite: Button = itemView.findViewById(R.id.btnInvite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]
        holder.tvName.text = friend.username

        if (friend.isOnline) {
            holder.tvStatus.text = "Online"
            holder.tvStatus.setTextColor(
                holder.itemView.context.getColor(R.color.difficulty_easy)
            )
            holder.onlineDot.visibility = View.VISIBLE
            holder.onlineDot.setBackgroundResource(R.drawable.online_dot)
            holder.btnInvite.isEnabled = true
            holder.btnInvite.alpha = 1f
        } else {
            holder.tvStatus.text = "Offline"
            holder.tvStatus.setTextColor(
                holder.itemView.context.getColor(R.color.clay_text_muted)
            )
            holder.onlineDot.visibility = View.GONE
            holder.btnInvite.isEnabled = false
            holder.btnInvite.alpha = 0.4f
        }

        holder.ivAvatar.load(AvatarUtils.getAvatarUrl(friend.avatarId)) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
            transformations(CircleCropTransformation())
        }

        holder.btnInvite.setOnClickListener {
            onInviteClick(friend)
        }
    }

    override fun getItemCount() = friends.size
}