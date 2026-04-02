package com.quizduel.app.ui.duel

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
import com.quizduel.app.data.model.BattleHistory
import com.quizduel.app.ui.profile.AvatarUtils
import java.text.SimpleDateFormat
import java.util.*

class BattleHistoryAdapter(
    private val battles: List<BattleHistory>,
    private val myUsername: String,
    private val myAvatarId: Int,
    private val onClick: (BattleHistory) -> Unit
) : RecyclerView.Adapter<BattleHistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTopicName: TextView = itemView.findViewById(R.id.tvTopicName)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val ivMyAvatar: ImageView = itemView.findViewById(R.id.ivMyAvatar)
        val tvMyName: TextView = itemView.findViewById(R.id.tvMyName)
        val tvMyScore: TextView = itemView.findViewById(R.id.tvMyScore)
        val tvResult: TextView = itemView.findViewById(R.id.tvResult)
        val ivOppAvatar: ImageView = itemView.findViewById(R.id.ivOppAvatar)
        val tvOppName: TextView = itemView.findViewById(R.id.tvOppName)
        val tvOppScore: TextView = itemView.findViewById(R.id.tvOppScore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_battle_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val battle = battles[position]

        holder.tvTopicName.text = battle.topicName
        holder.tvDate.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            .format(Date(battle.timestamp))

        holder.tvMyName.text = myUsername
        holder.tvMyScore.text = battle.myScore.toString()
        holder.tvOppName.text = battle.opponentName
        holder.tvOppScore.text = battle.opponentScore.toString()

        // My avatar
        holder.ivMyAvatar.load(AvatarUtils.getAvatarUrl(myAvatarId)) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
            transformations(CircleCropTransformation())
        }

        // Opponent avatar
        holder.ivOppAvatar.load(AvatarUtils.getAvatarUrl(battle.opponentAvatarId)) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
            transformations(CircleCropTransformation())
        }

        // Result badge
        when (battle.result) {
            "win" -> {
                holder.tvResult.text = "WIN"
                holder.tvResult.setBackgroundResource(R.drawable.circle_red_bg)
            }
            "loss" -> {
                holder.tvResult.text = "LOSS"
                holder.tvResult.setBackgroundResource(R.drawable.circle_gray_bg)
            }
            else -> {
                holder.tvResult.text = "DRAW"
                holder.tvResult.setBackgroundResource(R.drawable.circle_gray_bg)
            }
        }

        holder.itemView.setOnClickListener { onClick(battle) }
    }

    override fun getItemCount() = battles.size
}