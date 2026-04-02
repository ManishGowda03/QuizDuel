package com.quizduel.app.ui.duel

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.quizduel.app.R
import com.quizduel.app.data.model.Room
import com.quizduel.app.data.model.RoomPlayer
import com.quizduel.app.data.model.Topic
import com.quizduel.app.databinding.ActivityCreateRoomBinding
import android.content.Intent

class CreateRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateRoomBinding
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val allTopics = mutableListOf<Topic>()
    private val filteredTopics = mutableListOf<Topic>()
    private lateinit var topicAdapter: BattleTopicAdapter

    private val categories = listOf("All", "Programming", "Database", "Interview")
    private var selectedCategory = "All"

    private var selectedTopic: Topic? = null
    private var questionCount = 10
    private val MIN_QUESTIONS = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        setupTopicsGrid()
        setupCategoryFilter()
        setupQuestionCountControls()
        loadTopics()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCreateRoom.setOnClickListener { createRoom() }
    }

    private fun setupCategoryFilter() {
        categories.forEach { category ->
            val chip = layoutInflater.inflate(
                R.layout.item_battle_category_chip,
                binding.categoryContainer,
                false
            ) as TextView
            chip.text = category
            chip.tag = category
            chip.setOnClickListener { onCategorySelected(category, chip) }
            binding.categoryContainer.addView(chip)
        }
        // Select "All" by default
        (binding.categoryContainer.getChildAt(0) as? TextView)?.let {
            onCategorySelected("All", it)
        }
    }

    private fun onCategorySelected(category: String, selectedChip: TextView) {
        selectedCategory = category
        for (i in 0 until binding.categoryContainer.childCount) {
            val chip = binding.categoryContainer.getChildAt(i) as? TextView
            chip?.isSelected = (chip?.text == category)
        }
        filterTopics()
    }

    private fun setupTopicsGrid() {
        topicAdapter = BattleTopicAdapter(filteredTopics) { topic ->
            onTopicSelected(topic)
        }
        binding.rvTopics.layoutManager = GridLayoutManager(this, 2)
        binding.rvTopics.adapter = topicAdapter
    }

    private fun onTopicSelected(topic: Topic) {
        selectedTopic = topic
        val available = topic.questionCount
        questionCount = when {
            available <= 0 -> MIN_QUESTIONS
            questionCount > available -> available
            questionCount < MIN_QUESTIONS -> MIN_QUESTIONS
            else -> questionCount
        }
        updateBottomPanel()
    }

    private fun setupQuestionCountControls() {
        binding.btnDecrease.setOnClickListener {
            if (questionCount > MIN_QUESTIONS) {
                questionCount--
                updateCountDisplay()
            }
        }
        binding.btnIncrease.setOnClickListener {
            val maxAllowed = selectedTopic?.questionCount ?: MIN_QUESTIONS
            if (questionCount < maxAllowed) {
                questionCount++
                updateCountDisplay()
            } else {
                Toast.makeText(this, "Max questions available: $maxAllowed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateBottomPanel() {
        val topic = selectedTopic ?: return
        binding.bottomPanel.visibility = View.VISIBLE
        binding.tvSelectedTopic.text = topic.name
        binding.tvQuestionCountAvailable.text = "(${topic.questionCount} available)"
        binding.tvStepLabel.text = "STEP 2 — SET QUESTION COUNT"
        updateCountDisplay()
    }

    private fun updateCountDisplay() {
        binding.tvQuestionCount.text = questionCount.toString()
    }

    private fun filterTopics() {
        filteredTopics.clear()
        val result = if (selectedCategory == "All") {
            allTopics.filter { it.isActive }
        } else {
            allTopics.filter { it.isActive && it.category == selectedCategory }
        }
        filteredTopics.addAll(result)
        topicAdapter.notifyDataSetChanged()
    }

    private fun loadTopics() {
        db.child("topics").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allTopics.clear()
                snapshot.children.forEach { child ->
                    child.getValue(Topic::class.java)?.let { allTopics.add(it) }
                }
                filterTopics()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@CreateRoomActivity, "Failed to load topics", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun createRoom() {
        val topic = selectedTopic
        if (topic == null) {
            Toast.makeText(this, "Please select a topic first", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: return
        binding.btnCreateRoom.isEnabled = false
        binding.btnCreateRoom.text = "Creating..."

        db.child("users").child(uid).child("username")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val username = snapshot.getValue(String::class.java) ?: "Player"
                    val roomCode = generateRoomCode()
                    val player1 = RoomPlayer(uid = uid, username = username)
                    val room = Room(
                        roomCode = roomCode,
                        topicId = topic.id,
                        topicName = topic.name,
                        questionCount = questionCount,
                        status = "waiting",
                        createdAt = System.currentTimeMillis(),
                        players = mapOf("player1" to player1)
                    )

                    db.child("rooms").child(roomCode).setValue(room)
                        .addOnSuccessListener {
                            val intent = Intent(this@CreateRoomActivity, WaitingLobbyActivity::class.java).apply {
                                putExtra("ROOM_CODE", roomCode)
                                putExtra("PLAYER_SLOT", "player1")
                            }
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener {
                            binding.btnCreateRoom.isEnabled = true
                            binding.btnCreateRoom.text = "Create Room"
                            Toast.makeText(this@CreateRoomActivity, "Failed to create room. Try again.", Toast.LENGTH_SHORT).show()
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.btnCreateRoom.isEnabled = true
                    binding.btnCreateRoom.text = "Create Room"
                    Toast.makeText(this@CreateRoomActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}