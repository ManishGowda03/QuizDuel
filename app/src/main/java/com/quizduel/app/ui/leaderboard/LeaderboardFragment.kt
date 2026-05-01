package com.quizduel.app.ui.leaderboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.quizduel.app.R
import com.quizduel.app.databinding.FragmentLeaderboardBinding
import android.util.Log

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val playersList = mutableListOf<LeaderboardPlayer>()
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private var myUid = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        myUid = auth.currentUser?.uid ?: ""
        setupRecyclerView()
        loadLeaderboard()
    }

    private fun setupRecyclerView() {
        leaderboardAdapter = LeaderboardAdapter(playersList, myUid) { player ->
            sendFriendRequest(player)
        }
        binding.rvLeaderboard.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLeaderboard.adapter = leaderboardAdapter
    }

    private fun loadLeaderboard() {

        db.child("users")
            .orderByChild("totalScore")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    if (_binding == null) return

                    playersList.clear()

                    for (child in snapshot.children) {

                        val role = child.child("role").getValue(String::class.java) ?: "player"
                        if (role == "admin") continue

                        val uid = child.key ?: continue
                        val username = child.child("username").getValue(String::class.java) ?: "Unknown"
                        val totalScore = child.child("totalScore").getValue(Int::class.java) ?: 0
                        val wins = child.child("wins").getValue(Int::class.java) ?: 0
                        val matches = child.child("matchesPlayed").getValue(Int::class.java) ?: 0
                        val avatarId = child.child("avatarId").getValue(Int::class.java) ?: 1

                        playersList.add(
                            LeaderboardPlayer(uid, username, totalScore, wins, matches, avatarId)
                        )
                    }

                    // sort descending
                    playersList.sortByDescending { it.totalScore }

                    // update UI
                    leaderboardAdapter.notifyDataSetChanged()

                    // optional
                    loadFriendStatusThenShow()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadFriendStatusThenShow() {
        if (myUid.isEmpty()) {
            showLeaderboard()
            return
        }

        db.child("friends").child(myUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return

                    val friendUids = snapshot.child("friendsList").children
                        .mapNotNull { it.key }.toSet()
                    val sentRequests = snapshot.child("sentRequests").children
                        .mapNotNull { it.key }.toSet()

                    playersList.forEach { player ->
                        player.friendStatus = when {
                            player.uid == myUid -> FriendStatus.SELF
                            friendUids.contains(player.uid) -> FriendStatus.FRIENDS
                            sentRequests.contains(player.uid) -> FriendStatus.PENDING
                            else -> FriendStatus.NONE
                        }
                    }

                    showLeaderboard()
                }
                override fun onCancelled(error: DatabaseError) {
                    showLeaderboard()
                }
            })
    }

    private fun showLeaderboard() {
        if (_binding == null) return
        leaderboardAdapter.notifyDataSetChanged()
        showFunStats()
    }

    private fun showFunStats() {
        if (_binding == null || playersList.isEmpty()) return
        binding.funStatsContainer.removeAllViews()

        val stats = listOf(
            Triple("🏆", "TOP SCORER", playersList.maxByOrNull { it.totalScore }),
            Triple("⚔️", "MOST WINS", playersList.maxByOrNull { it.wins }),
            Triple("🎮", "MOST MATCHES", playersList.maxByOrNull { it.matchesPlayed })
        )

        stats.forEach { (emoji, label, player) ->
            if (player == null) return@forEach

            val card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_fun_stat_card, binding.funStatsContainer, false)

            card.findViewById<TextView>(R.id.tvStatEmoji).text = emoji
            card.findViewById<TextView>(R.id.tvStatLabel).text = label
            card.findViewById<TextView>(R.id.tvStatValue).text = player.username
            card.findViewById<TextView>(R.id.tvStatSubValue).text = when (label) {
                "TOP SCORER" -> "${player.totalScore} pts"
                "MOST WINS" -> "${player.wins} wins"
                "MOST MATCHES" -> "${player.matchesPlayed} matches"
                else -> ""
            }

            binding.funStatsContainer.addView(card)
        }
    }

    private fun sendFriendRequest(player: LeaderboardPlayer) {
        if (myUid.isEmpty()) return

        db.child("users").child(myUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val myUsername = snapshot.child("username")
                        .getValue(String::class.java) ?: "Player"
                    val myAvatarId = snapshot.child("avatarId")
                        .getValue(Int::class.java) ?: 1

                    val request = mapOf(
                        "uid" to myUid,
                        "username" to myUsername,
                        "avatarId" to myAvatarId,
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.child("friends").child(player.uid)
                        .child("receivedRequests").child(myUid)
                        .setValue(request)
                        .addOnSuccessListener {
                            db.child("friends").child(myUid)
                                .child("sentRequests").child(player.uid)
                                .setValue(true)
                            Toast.makeText(
                                requireContext(),
                                "Friend request sent to ${player.username}!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                requireContext(),
                                "Failed to send request",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}