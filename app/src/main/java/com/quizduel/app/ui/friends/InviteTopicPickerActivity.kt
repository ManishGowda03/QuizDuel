package com.quizduel.app.ui.friends

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.quizduel.app.data.model.BattleInvite
import com.quizduel.app.data.model.Room
import com.quizduel.app.data.model.RoomPlayer
import com.quizduel.app.data.model.Topic
import com.quizduel.app.databinding.ActivityInviteTopicPickerBinding
import com.quizduel.app.ui.duel.BattleTopicAdapter

class InviteTopicPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInviteTopicPickerBinding
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val allTopics = mutableListOf<Topic>()
    private val filteredTopics = mutableListOf<Topic>()
    private lateinit var topicAdapter: BattleTopicAdapter

    private var selectedTopic: Topic? = null
    private var friendUid = ""
    private var friendUsername = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInviteTopicPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        friendUid = intent.getStringExtra("FRIEND_UID") ?: ""
        friendUsername = intent.getStringExtra("FRIEND_USERNAME") ?: ""

        binding.tvTitle.text = "Invite $friendUsername"
        binding.btnBack.setOnClickListener { finish() }

        setupTopicsGrid()
        loadTopics()

        binding.btnSendInvite.setOnClickListener { sendInvite() }
    }

    private fun setupTopicsGrid() {
        topicAdapter = BattleTopicAdapter(filteredTopics) { topic ->
            selectedTopic = topic
            binding.btnSendInvite.isEnabled = true
        }
        binding.rvTopics.layoutManager = GridLayoutManager(this, 2)
        binding.rvTopics.adapter = topicAdapter
    }

    private fun loadTopics() {
        db.child("topics").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allTopics.clear()
                snapshot.children.forEach { child ->
                    child.getValue(Topic::class.java)?.let {
                        if (it.isActive) allTopics.add(it)
                    }
                }
                filteredTopics.clear()
                filteredTopics.addAll(allTopics)
                topicAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun sendInvite() {
        val topic = selectedTopic ?: return
        val myUid = auth.currentUser?.uid ?: return

        binding.btnSendInvite.isEnabled = false
        binding.btnSendInvite.text = "Sending..."

        db.child("users").child(myUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val myUsername = snapshot.child("username")
                        .getValue(String::class.java) ?: "Player"
                    val myAvatarId = snapshot.child("avatarId")
                        .getValue(Int::class.java) ?: 1

                    val roomCode = generateRoomCode()
                    val player1 = RoomPlayer(uid = myUid, username = myUsername)
                    val room = Room(
                        roomCode = roomCode,
                        topicId = topic.id,
                        topicName = topic.name,
                        questionCount = minOf(10, topic.questionCount),
                        status = "waiting",
                        createdAt = System.currentTimeMillis(),
                        players = mapOf("player1" to player1)
                    )

                    // Create room first
                    db.child("rooms").child(roomCode).setValue(room)
                        .addOnSuccessListener {
                            // Send invite to friend
                            val inviteId = db.child("battleInvites")
                                .child(friendUid).push().key ?: return@addOnSuccessListener

                            val invite = BattleInvite(
                                inviteId = inviteId,
                                fromUid = myUid,
                                fromUsername = myUsername,
                                fromAvatarId = myAvatarId,
                                roomCode = roomCode,
                                topicName = topic.name,
                                topicId = topic.id,
                                questionCount = minOf(10, topic.questionCount),
                                status = "pending",
                                timestamp = System.currentTimeMillis()
                            )

                            db.child("battleInvites").child(friendUid)
                                .child(inviteId).setValue(invite)
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        this@InviteTopicPickerActivity,
                                        "Invite sent to $friendUsername!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    finish()
                                }
                        }
                        .addOnFailureListener {
                            binding.btnSendInvite.isEnabled = true
                            binding.btnSendInvite.text = "Send Invite"
                            Toast.makeText(
                                this@InviteTopicPickerActivity,
                                "Failed to send invite",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}