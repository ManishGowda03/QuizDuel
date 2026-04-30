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
import com.quizduel.app.data.model.FriendRequest
import com.quizduel.app.ui.profile.AvatarUtils


class FriendRequestAdapter(
    private val requests: List<FriendRequest>,
    private val onAccept: (FriendRequest) -> Unit,
    private val onReject: (FriendRequest) -> Unit
) : RecyclerView.Adapter<FriendRequestAdapter.RequestViewHolder>() {

    inner class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivRequestAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvRequestName)
        val btnAccept: Button = itemView.findViewById(R.id.btnAccept)
        val btnReject: Button = itemView.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requests[position]
        holder.tvName.text = request.username

        holder.ivAvatar.load(AvatarUtils.getAvatarUrl(request.avatarId)) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
            transformations(CircleCropTransformation())
        }

        holder.btnAccept.setOnClickListener { onAccept(request) }
        holder.btnReject.setOnClickListener { onReject(request) }
    }

    override fun getItemCount() = requests.size
}