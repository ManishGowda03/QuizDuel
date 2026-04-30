package com.quizduel.app.ui.duel

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.quizduel.app.R
import com.quizduel.app.data.model.Room
import com.quizduel.app.data.model.RoomPlayer
import com.quizduel.app.databinding.ActivityQuickMatchBinding
import com.quizduel.app.utils.NetworkUtils

class QuickMatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuickMatchBinding
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickMatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        binding.btnCancel.setOnClickListener { finish() }

        if (!NetworkUtils.isInternetAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        startQuickMatch()
    }

    private fun startQuickMatch() {
        val uid = auth.currentUser?.uid ?: return

        // THE FIX: Use .get() instead of addListenerForSingleValueEvent.
        // This FORCES the app to ignore local cache and ask the live Firebase server!
        db.child("quickmatch").child("waiting").get().addOnSuccessListener { snapshot ->
            val waitingRoomCode = snapshot.getValue(String::class.java)

            if (waitingRoomCode.isNullOrEmpty()) {
                // No one waiting — become the waiter
                createQuickMatchRoom(uid)
            } else {
                // Someone is waiting — DOUBLE CHECK the room actually exists on the server first
                db.child("rooms").child(waitingRoomCode).get().addOnSuccessListener { roomSnap ->
                    if (roomSnap.exists() && roomSnap.child("status").getValue(String::class.java) == "waiting") {
                        val p1Uid = roomSnap.child("players").child("player1").child("uid").getValue(String::class.java)

                        // Make sure we didn't just find our own stale room
                        if (p1Uid != uid) {
                            joinQuickMatchRoom(uid, waitingRoomCode)
                            return@addOnSuccessListener
                        }
                    }
                    // If the room was dead, full, or belonged to us, ignore it and create a new one
                    createQuickMatchRoom(uid)
                }.addOnFailureListener {
                    createQuickMatchRoom(uid)
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Connection error. Try again.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun createQuickMatchRoom(uid: String) {
        db.child("topics").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val activeTopics = snapshot.children
                    .mapNotNull { it.getValue(com.quizduel.app.data.model.Topic::class.java) }
                    .filter { it.isActive && it.questionCount > 0 }

                if (activeTopics.isEmpty()) {
                    Toast.makeText(this@QuickMatchActivity, "No topics available for battle", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }

                val topic = activeTopics.random()
                val questionCount = minOf(10, topic.questionCount)

                fetchUsernameAndCreate(uid, topic.id, topic.name, questionCount)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@QuickMatchActivity, "Failed to load topics", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
    }

    private fun fetchUsernameAndCreate(uid: String, topicId: String, topicName: String, questionCount: Int) {
        db.child("users").child(uid).child("username")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val username = snapshot.getValue(String::class.java) ?: "Player"
                    val roomCode = generateRoomCode()
                    val player1 = RoomPlayer(uid = uid, username = username)
                    val room = Room(
                        roomCode = roomCode,
                        topicId = topicId,
                        topicName = topicName,
                        questionCount = questionCount,
                        status = "waiting",
                        createdAt = System.currentTimeMillis(),
                        players = mapOf("player1" to player1)
                    )

                    db.child("rooms").child(roomCode).setValue(room)
                        .addOnSuccessListener {
                            // Advertise this room in quickmatch
                            db.child("quickmatch").child("waiting").setValue(roomCode)
                                .addOnSuccessListener {
                                    goToWaitingLobby(roomCode, "player1", isQuickMatch = true)
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this@QuickMatchActivity, "Failed to create match. Try again.", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                }

                override fun onCancelled(error: DatabaseError) { finish() }
            })
    }

    private fun joinQuickMatchRoom(uid: String, roomCode: String) {
        db.child("users").child(uid).child("username")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val username = snapshot.getValue(String::class.java) ?: "Player"
                    val player2 = RoomPlayer(uid = uid, username = username)

                    db.child("rooms").child(roomCode).child("players").child("player2")
                        .setValue(player2)
                        .addOnSuccessListener {
                            // Update status + clear quickmatch slot
                            db.child("rooms").child(roomCode).child("status").setValue("ready")
                            db.child("quickmatch").child("waiting").removeValue()
                            goToWaitingLobby(roomCode, "player2", isQuickMatch = true)
                        }
                        .addOnFailureListener {
                            Toast.makeText(this@QuickMatchActivity, "Failed to join match. Try again.", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                }

                override fun onCancelled(error: DatabaseError) { finish() }
            })
    }

    private fun goToWaitingLobby(roomCode: String, slot: String, isQuickMatch: Boolean) {
        val intent = Intent(this, WaitingLobbyActivity::class.java).apply {
            putExtra("ROOM_CODE", roomCode)
            putExtra("PLAYER_SLOT", slot)
            putExtra(WaitingLobbyActivity.EXTRA_IS_QUICK_MATCH, isQuickMatch)
        }
        startActivity(intent)
        finish()
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}