package com.quizduel.app.ui.duel

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.quizduel.app.data.model.BattleHistory
import com.quizduel.app.data.model.BattleHistoryQuestion
import com.quizduel.app.databinding.ActivityBattleHistoryBinding

class BattleHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBattleHistoryBinding
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val battlesList = mutableListOf<BattleHistory>()
    private var myUsername = ""
    private var myAvatarId = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBattleHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        binding.btnBack.setOnClickListener { finish() }

        val uid = auth.currentUser?.uid ?: return

        // Load my info then load history
        db.child("users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    myUsername = snapshot.child("username")
                        .getValue(String::class.java) ?: "You"
                    myAvatarId = snapshot.child("avatarId")
                        .getValue(Int::class.java) ?: 1
                    loadBattleHistory(uid)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadBattleHistory(uid: String) {
        db.child("battleHistory").child(uid)
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    battlesList.clear()

                    snapshot.children.forEach { child ->
                        val battleId = child.child("battleId")
                            .getValue(String::class.java) ?: ""
                        val opponentName = child.child("opponentName")
                            .getValue(String::class.java) ?: ""
                        val opponentUid = child.child("opponentUid")
                            .getValue(String::class.java) ?: ""
                        val opponentAvatarId = child.child("opponentAvatarId")
                            .getValue(Int::class.java) ?: 1
                        val myScore = child.child("myScore")
                            .getValue(Int::class.java) ?: 0
                        val opponentScore = child.child("opponentScore")
                            .getValue(Int::class.java) ?: 0
                        val result = child.child("result")
                            .getValue(String::class.java) ?: "draw"
                        val topicName = child.child("topicName")
                            .getValue(String::class.java) ?: ""
                        val questionCount = child.child("questionCount")
                            .getValue(Int::class.java) ?: 0
                        val timestamp = child.child("timestamp")
                            .getValue(Long::class.java) ?: 0L

                        val questions = mutableListOf<BattleHistoryQuestion>()
                        child.child("questions").children.forEach { q ->
                            questions.add(
                                BattleHistoryQuestion(
                                    questionText = q.child("questionText")
                                        .getValue(String::class.java) ?: "",
                                    optionA = q.child("optionA")
                                        .getValue(String::class.java) ?: "",
                                    optionB = q.child("optionB")
                                        .getValue(String::class.java) ?: "",
                                    optionC = q.child("optionC")
                                        .getValue(String::class.java) ?: "",
                                    optionD = q.child("optionD")
                                        .getValue(String::class.java) ?: "",
                                    correctAnswer = q.child("correctAnswer")
                                        .getValue(String::class.java) ?: "",
                                    myAnswer = q.child("myAnswer")
                                        .getValue(String::class.java) ?: "",
                                    opponentAnswer = q.child("opponentAnswer")
                                        .getValue(String::class.java) ?: ""
                                )
                            )
                        }

                        battlesList.add(
                            BattleHistory(
                                battleId, opponentName, opponentUid, opponentAvatarId,
                                myScore, opponentScore, result, topicName,
                                questionCount, timestamp, questions
                            )
                        )
                    }

                    // Show newest first
                    battlesList.reverse()

                    if (battlesList.isEmpty()) {
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.rvBattleHistory.visibility = View.GONE
                    } else {
                        binding.layoutEmpty.visibility = View.GONE
                        binding.rvBattleHistory.visibility = View.VISIBLE
                        setupRecyclerView()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupRecyclerView() {
        val adapter = BattleHistoryAdapter(battlesList, myUsername, myAvatarId) { battle ->
            startActivity(
                Intent(this, BattleHistoryDetailActivity::class.java).apply {
                    putExtra("BATTLE_ID", battle.battleId)
                    putExtra("OPPONENT_NAME", battle.opponentName)
                    putExtra("MY_SCORE", battle.myScore)
                    putExtra("OPP_SCORE", battle.opponentScore)
                    putExtra("RESULT", battle.result)
                    putExtra("TOPIC_NAME", battle.topicName)
                    putExtra("MY_NAME", myUsername)
                    // Pass questions as arrays
                    putStringArrayListExtra("Q_TEXTS",
                        ArrayList(battle.questions.map { it.questionText }))
                    putStringArrayListExtra("Q_CORRECT",
                        ArrayList(battle.questions.map { it.correctAnswer }))
                    putStringArrayListExtra("Q_MY_ANSWERS",
                        ArrayList(battle.questions.map { it.myAnswer }))
                    putStringArrayListExtra("Q_OPP_ANSWERS",
                        ArrayList(battle.questions.map { it.opponentAnswer }))
                    putStringArrayListExtra("Q_OPTIONS_A",
                        ArrayList(battle.questions.map { it.optionA }))
                    putStringArrayListExtra("Q_OPTIONS_B",
                        ArrayList(battle.questions.map { it.optionB }))
                    putStringArrayListExtra("Q_OPTIONS_C",
                        ArrayList(battle.questions.map { it.optionC }))
                    putStringArrayListExtra("Q_OPTIONS_D",
                        ArrayList(battle.questions.map { it.optionD }))
                }
            )
        }
        binding.rvBattleHistory.layoutManager = LinearLayoutManager(this)
        binding.rvBattleHistory.adapter = adapter
    }
}