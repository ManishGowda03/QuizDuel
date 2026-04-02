package com.quizduel.app.ui.leaderboard

import android.graphics.Color
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
import com.quizduel.app.ui.profile.AvatarUtils

data class LeaderboardPlayer(
    val uid: String = "",
    val username: String = "",
    val totalScore: Int = 0,
    val wins: Int = 0,
    val matchesPlayed: Int = 0,
    val avatarId: Int = 1,
    var friendStatus: FriendStatus = FriendStatus.NONE
)

enum class FriendStatus { NONE, PENDING, FRIENDS, SELF }

class LeaderboardAdapter(
    private val players: List<LeaderboardPlayer>,
    private val myUid: String,
    private val onAddFriend: (LeaderboardPlayer) -> Unit
) : RecyclerView.Adapter<LeaderboardAdapter.PlayerViewHolder>() {

    inner class PlayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rootCard: androidx.cardview.widget.CardView = itemView as androidx.cardview.widget.CardView
        val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvPlayerName)
        val tvScore: TextView = itemView.findViewById(R.id.tvScore)
        val tvWins: TextView = itemView.findViewById(R.id.tvWins)

        val tvMatches: TextView = itemView.findViewById(R.id.tvMatches)
        val btnAdd: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.btnAddFriend) // Updated type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard_player, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val player = players[position]
        val context = holder.itemView.context
        val rank = position + 1

        // Highlight the current user in light Indigo!
        if (player.uid == myUid) {
            holder.rootCard.setCardBackgroundColor(Color.parseColor("#E8EAFF"))
        } else {
            holder.rootCard.setCardBackgroundColor(context.getColor(R.color.clay_white))
        }

        // Medal or number
        holder.tvRank.text = when (rank) {
            1 -> "🥇"
            2 -> "🥈"
            3 -> "🥉"
            else -> "#$rank"
        }
        holder.tvRank.textSize = if (rank <= 3) 20f else 15f

        holder.tvName.text = player.username
        holder.tvScore.text = "${player.totalScore} pts"
        holder.tvWins.text = "${player.wins}W"
        holder.tvMatches.text = "${player.matchesPlayed}M"

        holder.ivAvatar.load(AvatarUtils.getAvatarUrl(player.avatarId)) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
            transformations(CircleCropTransformation())
        }

        // Hide Add Friend for self
        if (player.uid == myUid) {
            holder.btnAdd.visibility = View.GONE
            return
        }

        // Make sure there is never any text on the button
        holder.btnAdd.text = ""

        when (player.friendStatus) {
            FriendStatus.FRIENDS -> {
                // Show a standard Android 'Done/Check' or 'Person' icon
                holder.btnAdd.setIconResource(android.R.drawable.ic_menu_myplaces)
                holder.btnAdd.isEnabled = false
                holder.btnAdd.alpha = 0.5f
                holder.btnAdd.visibility = View.VISIBLE
            }
            FriendStatus.PENDING -> {
                // Show a Clock icon for pending
                holder.btnAdd.setIconResource(android.R.drawable.ic_menu_recent_history)
                holder.btnAdd.isEnabled = false
                holder.btnAdd.alpha = 0.5f
                holder.btnAdd.visibility = View.VISIBLE
            }
            FriendStatus.NONE -> {
                // Show the Plus icon for Add
                holder.btnAdd.setIconResource(android.R.drawable.ic_input_add)
                holder.btnAdd.isEnabled = true
                holder.btnAdd.alpha = 1f
                holder.btnAdd.visibility = View.VISIBLE
                holder.btnAdd.setOnClickListener {
                    player.friendStatus = FriendStatus.PENDING
                    notifyItemChanged(position)
                    onAddFriend(player)
                }
            }
            FriendStatus.SELF -> holder.btnAdd.visibility = View.GONE
        }
    }

    override fun getItemCount() = players.size
}