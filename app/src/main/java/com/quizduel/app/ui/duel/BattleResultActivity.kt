package com.quizduel.app.ui.duel

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.quizduel.app.R
import com.quizduel.app.databinding.ActivityBattleResultBinding
import com.quizduel.app.ui.home.HomeActivity
import com.quizduel.app.ui.home.DialogUtils

class BattleResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBattleResultBinding
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private var roomCode = ""
    private var mySlot = ""
    private var rematchListener: ValueEventListener? = null

    private var opponentLeftResultListener: ValueEventListener? = null

    private var rematchHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBattleResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        roomCode = intent.getStringExtra("ROOM_CODE") ?: ""
        mySlot = intent.getStringExtra("MY_SLOT") ?: "player1"
        val myScore = intent.getIntExtra("MY_SCORE", 0)
        val oppScore = intent.getIntExtra("OPP_SCORE", 0)
        val opponentSlot = if (mySlot == "player1") "player2" else "player1"

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        when {
            myScore > oppScore -> {
                // Indigo/Bold for Win
                binding.tvResultTitle.text = "🏆 You Won!"
                binding.tvResultTitle.setTextColor(
                    ContextCompat.getColor(this, R.color.clay_primary)
                )
                binding.tvResultSubtitle.text = "Excellent performance!"
            }
            myScore < oppScore -> {
                // RED for Loss - Bold and visible!
                binding.tvResultTitle.text = "😔 You Lost"
                binding.tvResultTitle.setTextColor(
                    ContextCompat.getColor(this, R.color.difficulty_hard) // Using your app's main red
                )
                binding.tvResultSubtitle.text = "Better luck next time!"
            }
            else -> {
                // Gray/Bold for Draw
                binding.tvResultTitle.text = "🤝 It's a Draw!"
                binding.tvResultTitle.setTextColor(
                    ContextCompat.getColor(this, R.color.clay_text_muted)
                )
                binding.tvResultSubtitle.text = "Evenly matched!"
            }
        }

        binding.tvMyScore.text = myScore.toString()
        binding.tvOppScore.text = oppScore.toString()

        db.child("rooms").child(roomCode).child("players")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    binding.tvMyName.text = snapshot.child(mySlot)
                        .child("username").getValue(String::class.java) ?: "You"
                    binding.tvOppName.text = snapshot.child(opponentSlot)
                        .child("username").getValue(String::class.java) ?: "Opponent"
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        saveBattleHistory(roomCode, mySlot, myScore, oppScore)
        updateUserStats(myScore, myScore > oppScore)
        listenForRematchRequest()
        listenForOpponentLeftOnResult()

        binding.btnGoHome.setOnClickListener { goHome() }

        binding.btnReviewAnswers.setOnClickListener {
            startActivity(Intent(this, BattleReviewActivity::class.java).apply {
                putExtra("ROOM_CODE", roomCode)
                putExtra("MY_SLOT", mySlot)
                putStringArrayListExtra("MY_ANSWERS",
                    intent.getStringArrayListExtra("MY_ANSWERS"))
                putStringArrayListExtra("OPP_ANSWERS",
                    intent.getStringArrayListExtra("OPP_ANSWERS"))
                putStringArrayListExtra("QUESTION_TEXTS",
                    intent.getStringArrayListExtra("QUESTION_TEXTS"))
                putStringArrayListExtra("CORRECT_ANSWERS",
                    intent.getStringArrayListExtra("CORRECT_ANSWERS"))
                putStringArrayListExtra("OPTIONS_A",
                    intent.getStringArrayListExtra("OPTIONS_A"))
                putStringArrayListExtra("OPTIONS_B",
                    intent.getStringArrayListExtra("OPTIONS_B"))
                putStringArrayListExtra("OPTIONS_C",
                    intent.getStringArrayListExtra("OPTIONS_C"))
                putStringArrayListExtra("OPTIONS_D",
                    intent.getStringArrayListExtra("OPTIONS_D"))
            })
        }

        // FIX ISSUE 4: Play Again sends rematch request to opponent
        binding.btnPlayAgain.setOnClickListener {
            sendRematchRequest()
        }
    }

    // ── Rematch system ────────────────────────────────────────────────────────

    private fun sendRematchRequest() {
        binding.btnPlayAgain.isEnabled = false
        binding.btnPlayAgain.text = "Waiting for opponent..."

        db.child("rooms").child(roomCode).child("rematch")
            .child(mySlot).setValue("requested")
            .addOnSuccessListener {
                Toast.makeText(this, "Rematch request sent!", Toast.LENGTH_SHORT).show()
            }

        // Auto-cancel after 30 seconds if opponent doesn't respond
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                val myRematchText = binding.btnPlayAgain.text.toString()
                if (myRematchText == "Waiting for opponent...") {
                    // Still waiting — opponent likely left
                    binding.btnPlayAgain.isEnabled = true
                    binding.btnPlayAgain.text = "PLAY AGAIN"
                    Toast.makeText(
                        this,
                        "No response from opponent. They may have left.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }, 30_000L)
    }

    private fun listenForRematchRequest() {
        val opponentSlot = if (mySlot == "player1") "player2" else "player1"
        var rematchDialogShown = false

        rematchListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val myRematch = snapshot.child(mySlot).getValue(String::class.java)
                val oppRematch = snapshot.child(opponentSlot).getValue(String::class.java)

                // Opponent requested rematch — show dialog to me
                if (oppRematch == "requested" && myRematch == null && !rematchDialogShown) {
                    rematchDialogShown = true
                    showRematchReceivedDialog()
                }

                // Both requested — start rematch
                if (myRematch == "requested" && oppRematch == "requested") {
                    startRematch()
                }

                // FIX ISSUE 2: Opponent declined my rematch request
                if (myRematch == "requested" && oppRematch == "declined" && !isFinishing) {
                    rematchHandled = true
                    rematchListener?.let {
                        db.child("rooms").child(roomCode).child("rematch").removeEventListener(it)
                    }
                    // Reset button
                    binding.btnPlayAgain.isEnabled = true
                    binding.btnPlayAgain.text = "PLAY AGAIN"
                    AlertDialog.Builder(this@BattleResultActivity)
                        .setTitle("Rematch Declined")
                        .setMessage("Your opponent declined the rematch.")
                        .setPositiveButton("OK") { _, _ -> }
                        .show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        db.child("rooms").child(roomCode).child("rematch")
            .addValueEventListener(rematchListener!!)
    }

    private fun showRematchReceivedDialog() {
        if (isFinishing) return
        DialogUtils.showRematchRequestDialog(
            context = this,
            onAccept = {
                // Keep your existing accept logic here!
                db.child("rooms").child(roomCode).child("rematch")
                    .child(mySlot).setValue("requested")
            },
            onDecline = {
                // Keep your existing decline logic here!
                db.child("rooms").child(roomCode).child("rematch")
                    .child(mySlot).setValue("declined")
                goHome()
            }
        )
    }

    private fun startRematch() {
        rematchListener?.let {
            db.child("rooms").child(roomCode).child("rematch").removeEventListener(it)
        }

        // FIX ISSUE 5: Reset room for rematch properly
        // Keep same topic, players — just reset scores and game state
        val resetUpdates = hashMapOf<String, Any>(
            "status" to "ready",
            "phase" to "waiting",
            "currentQuestionIndex" to 0,
            "questionOrder" to mapOf<String, Any>(),
            "player2Ready" to false,
            "rematch" to mapOf<String, Any>(),
            "players/$mySlot/score" to 0,
            "players/$mySlot/answered" to 0
        )

        db.child("rooms").child(roomCode).updateChildren(resetUpdates)
            .addOnSuccessListener {
                // Both go to waiting lobby — same room same slots
                startActivity(
                    Intent(this, WaitingLobbyActivity::class.java).apply {
                        putExtra("ROOM_CODE", roomCode)
                        putExtra("PLAYER_SLOT", mySlot)
                    }
                )
                finish()
            }
    }

    private fun listenForOpponentLeftOnResult() {
        opponentLeftResultListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status")
                    .getValue(String::class.java) ?: return
                if (status == "abandoned" && !isFinishing && !rematchHandled) {
                    opponentLeftResultListener?.let {
                        db.child("rooms").child(roomCode).removeEventListener(it)
                    }
                    // Reset play again button if it was waiting
                    binding.btnPlayAgain.isEnabled = true
                    binding.btnPlayAgain.text = "PLAY AGAIN"

                    if (!isFinishing) {
                        AlertDialog.Builder(this@BattleResultActivity)
                            .setTitle("Opponent Left")
                            .setMessage("Your opponent has left the game.")
                            .setCancelable(false)
                            .setPositiveButton("OK") { _, _ -> }
                            .show()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("rooms").child(roomCode).addValueEventListener(opponentLeftResultListener!!)
    }

    private fun saveBattleHistory(
        roomCode: String,
        mySlot: String,
        myScore: Int,
        oppScore: Int
    ) {
        val uid = auth.currentUser?.uid ?: return
        val opponentSlot = if (mySlot == "player1") "player2" else "player1"

        val myAnswers = intent.getStringArrayListExtra("MY_ANSWERS") ?: arrayListOf()
        val oppAnswers = intent.getStringArrayListExtra("OPP_ANSWERS") ?: arrayListOf()
        val questionTexts = intent.getStringArrayListExtra("QUESTION_TEXTS") ?: arrayListOf()
        val correctAnswers = intent.getStringArrayListExtra("CORRECT_ANSWERS") ?: arrayListOf()
        val optionsA = intent.getStringArrayListExtra("OPTIONS_A") ?: arrayListOf()
        val optionsB = intent.getStringArrayListExtra("OPTIONS_B") ?: arrayListOf()
        val optionsC = intent.getStringArrayListExtra("OPTIONS_C") ?: arrayListOf()
        val optionsD = intent.getStringArrayListExtra("OPTIONS_D") ?: arrayListOf()

        // Get opponent info from Firebase then save
        db.child("rooms").child(roomCode)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val opponentName = snapshot.child("players").child(opponentSlot)
                        .child("username").getValue(String::class.java) ?: "Opponent"
                    val opponentUid = snapshot.child("players").child(opponentSlot)
                        .child("uid").getValue(String::class.java) ?: ""
                    val topicName = snapshot.child("topicName")
                        .getValue(String::class.java) ?: ""
                    val questionCount = snapshot.child("questionCount")
                        .getValue(Int::class.java) ?: 0

                    // Get opponent avatar from their user node
                    db.child("users").child(opponentUid).child("avatarId")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(avatarSnap: DataSnapshot) {
                                val opponentAvatarId = avatarSnap.getValue(Int::class.java) ?: 1

                                val result = when {
                                    myScore > oppScore -> "win"
                                    myScore < oppScore -> "loss"
                                    else -> "draw"
                                }

                                // Build questions list
                                val questions = mutableListOf<Map<String, Any>>()
                                for (i in questionTexts.indices) {
                                    questions.add(
                                        mapOf(
                                            "questionText" to (questionTexts.getOrNull(i) ?: ""),
                                            "optionA" to (optionsA.getOrNull(i) ?: ""),
                                            "optionB" to (optionsB.getOrNull(i) ?: ""),
                                            "optionC" to (optionsC.getOrNull(i) ?: ""),
                                            "optionD" to (optionsD.getOrNull(i) ?: ""),
                                            "correctAnswer" to (correctAnswers.getOrNull(i) ?: ""),
                                            "myAnswer" to (myAnswers.getOrNull(i) ?: "NONE"),
                                            "opponentAnswer" to (oppAnswers.getOrNull(i) ?: "NONE")
                                        )
                                    )
                                }

                                val battleId = db.child("battleHistory").child(uid).push().key
                                    ?: return@onDataChange

                                val historyEntry = mapOf(
                                    "battleId" to battleId,
                                    "opponentName" to opponentName,
                                    "opponentUid" to opponentUid,
                                    "opponentAvatarId" to opponentAvatarId,
                                    "myScore" to myScore,
                                    "opponentScore" to oppScore,
                                    "result" to result,
                                    "topicName" to topicName,
                                    "questionCount" to questionCount,
                                    "timestamp" to System.currentTimeMillis(),
                                    "questions" to questions
                                )

                                db.child("battleHistory").child(uid)
                                    .child(battleId).setValue(historyEntry)
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateUserStats(myScore: Int, won: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.child("users").child(uid)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val prevScore = snapshot.child("totalScore").getValue(Int::class.java) ?: 0
                val prevMatches = snapshot.child("matchesPlayed").getValue(Int::class.java) ?: 0
                val prevWins = snapshot.child("wins").getValue(Int::class.java) ?: 0
                userRef.child("totalScore").setValue(prevScore + myScore)
                userRef.child("matchesPlayed").setValue(prevMatches + 1)
                if (won) userRef.child("wins").setValue(prevWins + 1)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun goHome() {
        rematchListener?.let {
            db.child("rooms").child(roomCode).child("rematch").removeEventListener(it)
        }
        opponentLeftResultListener?.let {
            db.child("rooms").child(roomCode).removeEventListener(it)
        }
        // Tell opponent you left
        db.child("rooms").child(roomCode).child("status").setValue("abandoned")

        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    override fun onBackPressed() { goHome() }

    override fun onDestroy() {
        super.onDestroy()
        rematchListener?.let {
            db.child("rooms").child(roomCode).child("rematch").removeEventListener(it)
        }
        opponentLeftResultListener?.let {
            db.child("rooms").child(roomCode).removeEventListener(it)
        }
    }
}