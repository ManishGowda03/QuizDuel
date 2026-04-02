package com.quizduel.app.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.decode.SvgDecoder
import coil.transform.CircleCropTransformation
import com.quizduel.app.R
import com.quizduel.app.ui.profile.AvatarUtils

data class AdminUser(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val avatarId: Int = 1,
    val totalScore: Int = 0,
    val wins: Int = 0,
    val matchesPlayed: Int = 0
)

class AdminUsersAdapter(
    private val users: List<AdminUser>,
    private val onDelete: (AdminUser) -> Unit
) : RecyclerView.Adapter<AdminUsersAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivUserAvatar)
        val tvUsername: TextView = itemView.findViewById(R.id.tvUserUsername)
        val tvEmail: TextView = itemView.findViewById(R.id.tvUserEmail)
        val tvScore: TextView = itemView.findViewById(R.id.tvUserScore)
        val tvWins: TextView = itemView.findViewById(R.id.tvUserWins)
        val tvMatches: TextView = itemView.findViewById(R.id.tvUserMatches)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDeleteUser)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]

        holder.tvUsername.text = user.username
        holder.tvEmail.text = user.email
        holder.tvScore.text = "${user.totalScore} pts"
        holder.tvWins.text = "${user.wins} wins"
        holder.tvMatches.text = "${user.matchesPlayed} matches"

        holder.ivAvatar.load(AvatarUtils.getAvatarUrl(user.avatarId)) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
            transformations(CircleCropTransformation())
        }

        holder.btnDelete.setOnClickListener { onDelete(user) }
    }

    override fun getItemCount() = users.size
}