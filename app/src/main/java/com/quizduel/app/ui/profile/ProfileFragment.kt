package com.quizduel.app.ui.profile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import coil.decode.SvgDecoder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.quizduel.app.R
import com.quizduel.app.databinding.FragmentProfileBinding
import com.quizduel.app.ui.friends.FriendsActivity
import coil.transform.CircleCropTransformation

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private var currentAvatarId = 1
    private var tempAvatarId = 1       // tracks selection before save
    private var currentUsername = ""
    private var isUsernameAvailable = false
    private var usernameCheckListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserData()
        setupAvatarPicker()
        setupEditSection()
        setupClickListeners()
        listenForFriendRequests()
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        db.child("users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    currentUsername = snapshot.child("username")
                        .getValue(String::class.java) ?: "Player"
                    val email = snapshot.child("email")
                        .getValue(String::class.java) ?: ""
                    val totalScore = snapshot.child("totalScore")
                        .getValue(Int::class.java) ?: 0
                    val wins = snapshot.child("wins")
                        .getValue(Int::class.java) ?: 0
                    val matchesPlayed = snapshot.child("matchesPlayed")
                        .getValue(Int::class.java) ?: 0
                    currentAvatarId = snapshot.child("avatarId")
                        .getValue(Int::class.java) ?: 1
                    tempAvatarId = currentAvatarId

                    binding.tvUsername.text = currentUsername
                    binding.tvEmail.text = email
                    binding.tvTotalScore.text = totalScore.toString()
                    binding.tvWins.text = wins.toString()
                    binding.tvMatchesPlayed.text = matchesPlayed.toString()
                    loadAvatar(currentAvatarId)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadAvatar(avatarId: Int) {
        if (_binding == null) return
        binding.ivAvatar.load(AvatarUtils.getAvatarUrl(avatarId)) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
            transformations(coil.transform.CircleCropTransformation())
        }
    }

    private fun setupAvatarPicker() {
        val avatarAdapter = AvatarAdapter(AvatarUtils.getAllAvatars(), currentAvatarId) { avatarId ->
            tempAvatarId = avatarId
            // Show preview in profile image while editing
            binding.ivAvatar.load(AvatarUtils.getAvatarUrl(avatarId)) {
                decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
            }
        }
        binding.rvAvatars.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvAvatars.adapter = avatarAdapter
    }

    private fun setupEditSection() {
        // Show edit section when pencil clicked
        binding.btnEditAvatar.setOnClickListener {
            val isVisible = binding.cardEditSection.visibility == View.VISIBLE
            if (isVisible) {
                hideEditSection()
            } else {
                showEditSection()
            }
        }

        // Cancel button
        binding.btnCancelEdit.setOnClickListener {
            hideEditSection()
            // Revert avatar preview
            tempAvatarId = currentAvatarId
            loadAvatar(currentAvatarId)
        }

        // Save button
        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        // Live username check
        binding.etUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val newUsername = s.toString().trim()
                if (newUsername.isEmpty()) {
                    binding.tvUsernameStatus.visibility = View.GONE
                    isUsernameAvailable = false
                    return
                }
                if (newUsername == currentUsername) {
                    // Same as current — treat as available
                    binding.tvUsernameStatus.text = "✓ Current username"
                    binding.tvUsernameStatus.setTextColor(
                        requireContext().getColor(R.color.difficulty_easy)
                    )
                    binding.tvUsernameStatus.visibility = View.VISIBLE
                    isUsernameAvailable = true
                    return
                }
                checkUsernameAvailability(newUsername)
            }
        })
    }

    private fun showEditSection() {
        binding.cardEditSection.visibility = View.VISIBLE
        // Pre-fill current username as hint
        binding.etUsername.hint = currentUsername
        binding.etUsername.setText("")
        binding.tvUsernameStatus.visibility = View.GONE
        isUsernameAvailable = true  // allow saving with same username
    }

    private fun hideEditSection() {
        binding.cardEditSection.visibility = View.GONE
        binding.etUsername.setText("")
        binding.tvUsernameStatus.visibility = View.GONE
    }

    private fun checkUsernameAvailability(username: String) {
        isUsernameAvailable = false
        binding.tvUsernameStatus.text = "Checking..."
        binding.tvUsernameStatus.setTextColor(
            requireContext().getColor(R.color.text_secondary)
        )
        binding.tvUsernameStatus.visibility = View.VISIBLE

        db.child("users").orderByChild("username").equalTo(username)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    if (snapshot.exists()) {
                        // Username taken
                        binding.tvUsernameStatus.text = "✗ Username already taken"
                        binding.tvUsernameStatus.setTextColor(
                            requireContext().getColor(R.color.difficulty_hard)
                        )
                        isUsernameAvailable = false
                    } else {
                        // Username available
                        binding.tvUsernameStatus.text = "✓ Username available"
                        binding.tvUsernameStatus.setTextColor(
                            requireContext().getColor(R.color.difficulty_easy)
                        )
                        isUsernameAvailable = true
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun saveProfile() {
        val uid = auth.currentUser?.uid ?: return
        val newUsername = binding.etUsername.text.toString().trim()
            .ifEmpty { currentUsername }  // if empty keep current

        if (!isUsernameAvailable && newUsername != currentUsername) {
            Toast.makeText(requireContext(), "Username is not available", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSaveProfile.isEnabled = false
        binding.btnSaveProfile.text = "Saving..."

        val updates = mapOf(
            "username" to newUsername,
            "avatarId" to tempAvatarId
        )

        db.child("users").child(uid).updateChildren(updates)
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                currentUsername = newUsername
                currentAvatarId = tempAvatarId
                binding.tvUsername.text = currentUsername
                loadAvatar(currentAvatarId)
                hideEditSection()
                binding.btnSaveProfile.isEnabled = true
                binding.btnSaveProfile.text = "Save"
                Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.btnSaveProfile.isEnabled = true
                binding.btnSaveProfile.text = "Save"
                Toast.makeText(requireContext(), "Failed to update. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupClickListeners() {
        binding.btnFriends.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), FriendsActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }


    private fun showLogoutDialog() {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_logout_2)

        // Make the background transparent so the rounded corners show perfectly
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnCancel = dialog.findViewById<android.widget.TextView>(R.id.btnCancelLogout)
        val btnConfirm = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmLogout)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            setOfflineStatus()
            auth.signOut()
            val intent = android.content.Intent(
                requireContext(),
                com.quizduel.app.ui.auth.LoginActivity::class.java
            )
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }

        dialog.show()
    }

    private fun setOfflineStatus() {
        val uid = auth.currentUser?.uid ?: return
        db.child("users").child(uid).child("isOnline").setValue(false)
        db.child("users").child(uid).child("lastSeen")
            .setValue(System.currentTimeMillis())
    }

    private fun listenForFriendRequests() {
        val uid = auth.currentUser?.uid ?: return
        db.child("friends").child(uid).child("receivedRequests")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    binding.friendsBadge.visibility =
                        if (snapshot.exists() && snapshot.childrenCount > 0) View.VISIBLE
                        else View.GONE
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}