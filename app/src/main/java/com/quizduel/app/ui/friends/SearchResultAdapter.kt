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

class SearchResultAdapter(
    private val results: List<FriendModel>,
    private val onAddFriend: (FriendModel) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.SearchViewHolder>() {

    // Track sent requests so button changes to Pending
    private val sentRequests = mutableSetOf<String>()

    inner class SearchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivSearchAvatar)
        val tvUsername: TextView = itemView.findViewById(R.id.tvSearchUsername)
        val btnAdd: Button = itemView.findViewById(R.id.btnAddFriend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val user = results[position]
        holder.tvUsername.text = user.username

        holder.ivAvatar.load(AvatarUtils.getAvatarUrl(user.avatarId)) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
            transformations(CircleCropTransformation())
        }

        if (sentRequests.contains(user.uid)) {
            holder.btnAdd.text = "Pending"
            holder.btnAdd.isEnabled = false
            holder.btnAdd.alpha = 0.6f
        } else {
            holder.btnAdd.text = "Add Friend"
            holder.btnAdd.isEnabled = true
            holder.btnAdd.alpha = 1f
            holder.btnAdd.setOnClickListener {
                sentRequests.add(user.uid)
                notifyItemChanged(position)
                onAddFriend(user)
            }
        }
    }

    override fun getItemCount() = results.size
}