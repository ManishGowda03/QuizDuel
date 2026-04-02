package com.quizduel.app.ui.duel

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.quizduel.app.databinding.FragmentBattleBinding

class BattleFragment : Fragment() {

    private var _binding: FragmentBattleBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBattleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardQuickMatch.setOnClickListener {
            startActivity(Intent(requireContext(), QuickMatchActivity::class.java))
        }

        binding.cardCreateRoom.setOnClickListener {
            startActivity(Intent(requireContext(), CreateRoomActivity::class.java))
        }

        binding.cardJoinRoom.setOnClickListener {
            startActivity(Intent(requireContext(), JoinRoomActivity::class.java))
        }

        binding.cardBattleHistory.setOnClickListener {
            startActivity(Intent(requireContext(), BattleHistoryActivity::class.java))
        }

        binding.btnViewHistory.setOnClickListener {
            startActivity(Intent(requireContext(), BattleHistoryActivity::class.java))
        }

        loadHistoryCount()
    }

    private fun loadHistoryCount() {
        val uid = auth.currentUser?.uid ?: return
        db.child("battleHistory").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    val count = snapshot.childrenCount.toInt()
                    binding.tvHistoryCount.text = when (count) {
                        0 -> "No battles played yet"
                        1 -> "1 Battle played"
                        else -> "$count Battles played"
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