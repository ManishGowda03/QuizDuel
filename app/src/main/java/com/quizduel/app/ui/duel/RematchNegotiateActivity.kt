package com.quizduel.app.ui.duel

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.quizduel.app.R
import com.quizduel.app.data.model.Room
import com.quizduel.app.data.model.RoomPlayer
import com.quizduel.app.data.model.Topic
import com.quizduel.app.databinding.ActivityRematchNegotiateBinding
import com.quizduel.app.ui.home.HomeActivity

class RematchNegotiateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRematchNegotiateBinding
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private lateinit var roomCode: String
    private lateinit var mySlot: String
    private lateinit var opponentSlot: String

    private val allTopics = mutableListOf<Topic>()
    private val filteredTopics = mutableListOf<Topic>()
    private lateinit var topicAdapter: BattleTopicAdapter
    private val categories = listOf(
        "All","Python","Java","DSA","OS","DBMS","Networks","Interview"
    )
    private var selectedCategory = "All"
    private var selectedTopic: Topic? = null

    private var rematchListener: ValueEventListener? = null
    private var iAmRequester = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRematchNegotiateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        roomCode = intent.getStringExtra("ROOM_CODE") ?: run { finish(); return }
        mySlot = intent.getStringExtra("MY_SLOT") ?: "player1"
        opponentSlot = if (mySlot == "player1") "player2" else "player1"

        binding.btnBack.setOnClickListener { goHome() }
        binding.btnCancelRequest.setOnClickListener {
            db.child("rooms").child(roomCode).child("rematch").removeValue()
            goHome()
        }
        binding.btnAccept.setOnClickListener { respondToRematch("accepted") }
        binding.btnDecline.setOnClickListener { respondToRematch("declined") }
        binding.btnConfirmTopic.setOnClickListener { confirmTopic() }

        checkAndSendRequest()
    }

    // ── FIX BUG 3: Single requester model ───────────────────────────────────

    private fun checkAndSendRequest() {
        // Check if opponent already sent a request
        db.child("rooms").child(roomCode).child("rematch")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val requester = snapshot.child("requester")
                        .getValue(String::class.java)
                    val response = snapshot.child("response")
                        .getValue(String::class.java) ?: ""

                    if (requester == null) {
                        // No request yet — I am the requester
                        iAmRequester = true
                        db.child("rooms").child(roomCode).child("rematch")
                            .child("requester").setValue(mySlot)
                        showWaitingPanel("Waiting for opponent to respond…")
                        listenForRematchState()
                    } else if (requester == opponentSlot && response.isEmpty()) {
                        // Opponent already requested — show respond panel to me
                        iAmRequester = false
                        showRespondPanel()
                        listenForRematchState()
                    } else {
                        // Edge case — just listen
                        listenForRematchState()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun listenForRematchState() {
        rematchListener?.let {
            db.child("rooms").child(roomCode).removeEventListener(it)
        }

        rematchListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rematch = snapshot.child("rematch")
                val requester = rematch.child("requester")
                    .getValue(String::class.java) ?: return
                val response = rematch.child("response")
                    .getValue(String::class.java) ?: ""

                when {
                    // Declined
                    response == "declined" -> {
                        showMessage(
                            if (iAmRequester) "Opponent declined the rematch."
                            else "You declined."
                        )
                        goHomeDelayed()
                    }

                    // Both accepted — show topic selection
                    response == "accepted" -> {
                        showTopicSelectPanel()

                        val myTopicId = rematch.child("${mySlot}Topic")
                            .getValue(String::class.java) ?: ""
                        val oppTopicId = rematch.child("${opponentSlot}Topic")
                            .getValue(String::class.java) ?: ""

                        binding.tvOpponentTopicStatus.text =
                            if (oppTopicId.isNotEmpty()) "Opponent: topic selected ✓"
                            else "Opponent: choosing…"

                        if (myTopicId.isNotEmpty() && oppTopicId.isNotEmpty()) {
                            if (myTopicId == oppTopicId) {
                                if (mySlot == "player1") startNewRematchRoom(myTopicId)
                                else waitForNewRoom()
                            } else {
                                binding.tvOpponentTopicStatus.text =
                                    "⚠ Different topic selected! Please match your opponent."
                            }
                        }
                    }

                    // I'm requester — show waiting
                    requester == mySlot && response.isEmpty() -> {
                        showWaitingPanel("Waiting for opponent to respond…")
                    }

                    // Opponent is requester — show respond panel
                    requester == opponentSlot && response.isEmpty() -> {
                        showRespondPanel()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        db.child("rooms").child(roomCode).addValueEventListener(rematchListener!!)
    }

    private fun respondToRematch(response: String) {
        db.child("rooms").child(roomCode).child("rematch")
            .child("response").setValue(response)
        if (response == "declined") goHome()
        else showWaitingPanel("Accepted! Select a topic…")
    }

    private fun confirmTopic() {
        val topic = selectedTopic
        if (topic == null) {
            Toast.makeText(this, "Please select a topic first", Toast.LENGTH_SHORT).show()
            return
        }
        db.child("rooms").child(roomCode).child("rematch")
            .child("${mySlot}Topic").setValue(topic.id)
        binding.btnConfirmTopic.isEnabled = false
        binding.btnConfirmTopic.text = "Waiting for opponent…"
    }

    private fun startNewRematchRoom(topicId: String) {
        if (mySlot != "player1") return
        val topic = allTopics.find { it.id == topicId } ?: return
        val uid = auth.currentUser?.uid ?: return

        db.child("rooms").child(roomCode).child("players")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val p1Name = snapshot.child("player1").child("username")
                        .getValue(String::class.java) ?: "Player1"
                    val p2Uid = snapshot.child("player2").child("uid")
                        .getValue(String::class.java) ?: ""
                    val p2Name = snapshot.child("player2").child("username")
                        .getValue(String::class.java) ?: "Player2"

                    val newCode = generateRoomCode()
                    val room = Room(
                        roomCode = newCode,
                        topicId = topicId,
                        topicName = topic.name,
                        questionCount = minOf(10, topic.questionCount),
                        status = "ready",
                        createdAt = System.currentTimeMillis(),
                        players = mapOf(
                            "player1" to RoomPlayer(uid = uid, username = p1Name),
                            "player2" to RoomPlayer(uid = p2Uid, username = p2Name)
                        )
                    )
                    db.child("rooms").child(newCode).setValue(room)
                        .addOnSuccessListener {
                            db.child("rooms").child(roomCode).child("rematch")
                                .child("newRoomCode").setValue(newCode)
                            navigateToNewBattle(newCode)
                        }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun waitForNewRoom() {
        db.child("rooms").child(roomCode).child("rematch").child("newRoomCode")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val newCode = snapshot.getValue(String::class.java) ?: return
                    navigateToNewBattle(newCode)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun navigateToNewBattle(newRoomCode: String) {
        startActivity(Intent(this, WaitingLobbyActivity::class.java).apply {
            putExtra("ROOM_CODE", newRoomCode)
            putExtra("PLAYER_SLOT", mySlot)
        })
        finish()
    }

    // ── Topic selection ──────────────────────────────────────────────────────

    private fun setupCategoryFilter() {
        binding.categoryContainer.removeAllViews()
        categories.forEach { category ->
            val chip = layoutInflater.inflate(
                R.layout.item_battle_category_chip,
                binding.categoryContainer, false
            ) as TextView
            chip.text = category
            chip.setOnClickListener { onCategorySelected(category, chip) }
            binding.categoryContainer.addView(chip)
        }
        (binding.categoryContainer.getChildAt(0) as? TextView)?.let {
            onCategorySelected("All", it)
        }
    }

    private fun onCategorySelected(category: String, chip: TextView) {
        selectedCategory = category
        for (i in 0 until binding.categoryContainer.childCount) {
            (binding.categoryContainer.getChildAt(i) as? TextView)?.isSelected =
                (binding.categoryContainer.getChildAt(i) as? TextView)?.text == category
        }
        filterTopics()
    }

    private fun setupTopicsGrid() {
        topicAdapter = BattleTopicAdapter(filteredTopics) { topic -> selectedTopic = topic }
        binding.rvTopics.layoutManager = GridLayoutManager(this, 2)
        binding.rvTopics.adapter = topicAdapter
    }

    private fun filterTopics() {
        filteredTopics.clear()
        filteredTopics.addAll(
            if (selectedCategory == "All") allTopics.filter { it.isActive }
            else allTopics.filter { it.isActive && it.category == selectedCategory }
        )
        if (::topicAdapter.isInitialized) topicAdapter.notifyDataSetChanged()
    }

    private fun loadTopics() {
        db.child("topics").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allTopics.clear()
                snapshot.children.forEach { it.getValue(Topic::class.java)?.let { t -> allTopics.add(t) } }
                filterTopics()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // ── Panels ───────────────────────────────────────────────────────────────

    private fun showWaitingPanel(msg: String) {
        binding.panelWaiting.visibility = View.VISIBLE
        binding.panelRespond.visibility = View.GONE
        binding.panelTopicSelect.visibility = View.GONE
        binding.tvWaitingMsg.text = msg
        binding.tvWaitingSubMsg.text = ""
    }

    private fun showRespondPanel() {
        binding.panelWaiting.visibility = View.GONE
        binding.panelRespond.visibility = View.VISIBLE
        binding.panelTopicSelect.visibility = View.GONE
    }

    private fun showTopicSelectPanel() {
        if (binding.panelTopicSelect.visibility == View.VISIBLE) return
        binding.panelWaiting.visibility = View.GONE
        binding.panelRespond.visibility = View.GONE
        binding.panelTopicSelect.visibility = View.VISIBLE
        setupCategoryFilter()
        setupTopicsGrid()
        loadTopics()
    }

    private fun showMessage(msg: String) {
        binding.panelWaiting.visibility = View.VISIBLE
        binding.panelRespond.visibility = View.GONE
        binding.panelTopicSelect.visibility = View.GONE
        binding.tvWaitingMsg.text = msg
        binding.tvWaitingSubMsg.text = ""
    }

    private fun goHomeDelayed() {
        binding.root.postDelayed({ goHome() }, 2000)
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    override fun onDestroy() {
        super.onDestroy()
        rematchListener?.let {
            db.child("rooms").child(roomCode).removeEventListener(it)
        }
    }
}