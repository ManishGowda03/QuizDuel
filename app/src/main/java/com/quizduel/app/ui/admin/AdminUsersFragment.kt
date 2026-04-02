package com.quizduel.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.*
import com.quizduel.app.databinding.FragmentAdminUsersBinding

class AdminUsersFragment : Fragment() {

    private var _binding: FragmentAdminUsersBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseDatabase.getInstance().reference

    private val usersList = mutableListOf<AdminUser>()
    private lateinit var usersAdapter: AdminUsersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadUsers()
    }

    private fun setupRecyclerView() {
        usersAdapter = AdminUsersAdapter(usersList) { user ->
            showDeleteConfirmation(user)
        }
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = usersAdapter
    }

    private fun loadUsers() {
        db.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                usersList.clear()

                snapshot.children.forEach { child ->
                    val role = child.child("role").getValue(String::class.java) ?: "player"
                    if (role == "admin") return@forEach

                    val uid = child.child("uid").getValue(String::class.java) ?: ""
                    val username = child.child("username").getValue(String::class.java) ?: ""
                    val email = child.child("email").getValue(String::class.java) ?: ""
                    val avatarId = child.child("avatarId").getValue(Int::class.java) ?: 1
                    val totalScore = child.child("totalScore").getValue(Int::class.java) ?: 0
                    val wins = child.child("wins").getValue(Int::class.java) ?: 0
                    val matches = child.child("matchesPlayed").getValue(Int::class.java) ?: 0

                    if (uid.isNotEmpty()) {
                        usersList.add(AdminUser(uid, username, email, avatarId,
                            totalScore, wins, matches))
                    }
                }

                usersList.sortBy { it.username }
                usersAdapter.notifyDataSetChanged()
                binding.tvUserCount.text = "${usersList.size} registered players"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showDeleteConfirmation(user: AdminUser) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete User")
            .setMessage("Are you sure you want to delete ${user.username}? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteUser(user) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUser(user: AdminUser) {
        db.child("users").child(user.uid).removeValue()
            .addOnSuccessListener {
                db.child("friends").child(user.uid).removeValue()
                db.child("battleHistory").child(user.uid).removeValue()
                db.child("battleInvites").child(user.uid).removeValue()

                Toast.makeText(requireContext(), "${user.username} deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to delete user", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}