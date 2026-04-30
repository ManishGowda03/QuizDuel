package com.quizduel.app.ui.duel

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.quizduel.app.data.model.RoomPlayer
import com.quizduel.app.databinding.ActivityJoinRoomBinding
import com.quizduel.app.utils.NetworkUtils

class JoinRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJoinRoomBinding
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJoinRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        binding.btnBack.setOnClickListener { finish() }

        binding.etRoomCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                NetworkUtils.requireInternet(this) {
                    attemptJoin()
                }
                true
            } else false
        }

        binding.btnJoinRoom.setOnClickListener {
            NetworkUtils.requireInternet(this) {
                attemptJoin()
            }
        }
    }

    private fun attemptJoin() {

        if (!NetworkUtils.isInternetAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val code = binding.etRoomCode.text.toString().trim().uppercase()
        if (code.length != 6) {
            showError("Please enter a valid 6-character code")
            return
        }

        binding.tvError.visibility = View.GONE
        binding.btnJoinRoom.isEnabled = false
        binding.btnJoinRoom.text = "Joining..."

        val uid = auth.currentUser?.uid ?: return

        db.child("rooms").child(code).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    showError("Room not found. Check the code and try again.")
                    resetButton()
                    return
                }

                val status = snapshot.child("status").getValue(String::class.java)
                if (status != "waiting") {
                    showError("This room is no longer available.")
                    resetButton()
                    return
                }

                val player1Uid = snapshot.child("players").child("player1").child("uid")
                    .getValue(String::class.java)

                if (player1Uid == uid) {
                    showError("You cannot join your own room.")
                    resetButton()
                    return
                }

                // Fetch username then join
                db.child("users").child(uid).child("username")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot) {
                            val username = userSnapshot.getValue(String::class.java) ?: "Player"
                            val player2 = RoomPlayer(uid = uid, username = username)

                            db.child("rooms").child(code).child("players").child("player2")
                                .setValue(player2)
                                .addOnSuccessListener {
                                    db.child("rooms").child(code).child("status")
                                        .setValue("ready")
                                        .addOnSuccessListener {
                                            val intent = Intent(this@JoinRoomActivity, WaitingLobbyActivity::class.java).apply {
                                                putExtra("ROOM_CODE", code)
                                                putExtra("PLAYER_SLOT", "player2")
                                            }
                                            startActivity(intent)
                                            finish()
                                        }
                                }
                                .addOnFailureListener {
                                    showError("Failed to join room. Try again.")
                                    resetButton()
                                }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            showError("Error: ${error.message}")
                            resetButton()
                        }
                    })
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Connection error. Try again.")
                resetButton()
            }
        })
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun resetButton() {
        binding.btnJoinRoom.isEnabled = true
        binding.btnJoinRoom.text = "Join Room"
    }
}
