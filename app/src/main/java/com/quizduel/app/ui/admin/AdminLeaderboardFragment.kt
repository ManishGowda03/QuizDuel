package com.quizduel.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.decode.SvgDecoder
import coil.transform.CircleCropTransformation
import com.google.firebase.database.*
import com.quizduel.app.R
import com.quizduel.app.databinding.FragmentAdminLeaderboardBinding
import com.quizduel.app.ui.profile.AvatarUtils

class AdminLeaderboardFragment : Fragment() {

    private var _binding: FragmentAdminLeaderboardBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseDatabase.getInstance().reference

    private val playersList = mutableListOf<AdminUser>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        db.child("users").orderByChild("totalScore")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    playersList.clear()

                    snapshot.children.forEach { child ->
                        val role = child.child("role")
                            .getValue(String::class.java) ?: "player"
                        // Skip admins
                        if (role == "admin") return@forEach

                        val uid = child.child("uid")
                            .getValue(String::class.java) ?: ""
                        val username = child.child("username")
                            .getValue(String::class.java) ?: ""
                        val email = child.child("email")
                            .getValue(String::class.java) ?: ""
                        val avatarId = child.child("avatarId")
                            .getValue(Int::class.java) ?: 1
                        val totalScore = child.child("totalScore")
                            .getValue(Int::class.java) ?: 0
                        val wins = child.child("wins")
                            .getValue(Int::class.java) ?: 0
                        val matches = child.child("matchesPlayed")
                            .getValue(Int::class.java) ?: 0

                        if (uid.isNotEmpty()) {
                            playersList.add(AdminUser(uid, username, email,
                                avatarId, totalScore, wins, matches))
                        }
                    }

                    playersList.sortByDescending { it.totalScore }

                    binding.tvPlayerCount.text = "${playersList.size} players ranked"
                    setupRecyclerView()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupRecyclerView() {
        val adapter = AdminLeaderboardAdapter(playersList)
        binding.rvAdminLeaderboard.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAdminLeaderboard.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ── Admin Leaderboard Adapter ─────────────────────────────────────────────────
class AdminLeaderboardAdapter(
    private val players: List<AdminUser>
) : RecyclerView.Adapter<AdminLeaderboardAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRank: TextView = itemView.findViewById(R.id.tvAdminRank)
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivAdminAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvAdminPlayerName)
        val tvScore: TextView = itemView.findViewById(R.id.tvAdminScore)
        val tvWins: TextView = itemView.findViewById(R.id.tvAdminWins)
        val tvMatches: TextView = itemView.findViewById(R.id.tvAdminMatches)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_leaderboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val player = players[position]
        val rank = position + 1

        holder.tvRank.text = when (rank) {
            1 -> "🥇"
            2 -> "🥈"
            3 -> "🥉"
            else -> "#$rank"
        }
        holder.tvRank.textSize = if (rank <= 3) 20f else 13f

        holder.tvName.text = player.username
        holder.tvScore.text = "${player.totalScore} pts"
        holder.tvWins.text = "${player.wins} wins"
        holder.tvMatches.text = "${player.matchesPlayed} matches"

        holder.ivAvatar.load(AvatarUtils.getAvatarUrl(player.avatarId)) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
            transformations(CircleCropTransformation())
        }
    }

    override fun getItemCount() = players.size
}