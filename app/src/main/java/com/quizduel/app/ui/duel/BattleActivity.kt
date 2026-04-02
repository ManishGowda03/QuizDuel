package com.quizduel.app.ui.duel

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.database.*
import com.quizduel.app.R
import com.quizduel.app.data.model.Question
import com.quizduel.app.databinding.ActivityBattleBinding
import coil.load
import coil.decode.SvgDecoder
import com.quizduel.app.ui.profile.AvatarUtils
import com.quizduel.app.ui.home.DialogUtils

class BattleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBattleBinding
    private val db = FirebaseDatabase.getInstance().reference
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var roomCode: String
    private lateinit var mySlot: String
    private lateinit var opponentSlot: String

    private val questions = mutableListOf<Question>()
    private var currentIndex = 0
    private var myScore = 0
    private var opponentScore = 0

    private var myAnsweredTime: Long = -1L
    private var mySelectedOption: String = ANSWER_NONE
    private var inReviewPhase = false
    private var questionStartTime: Long = 0L
    private var lastHandledPhase = ""

    private var mainListener: ValueEventListener? = null
    private var opponentScoreListener: ValueEventListener? = null
    private var opponentLeftListener: ValueEventListener? = null

    private val myAnswersList = mutableListOf<String>()
    private val oppAnswersList = mutableListOf<String>()
    private val myPointsList = mutableListOf<Int>()
    private val oppPointsList = mutableListOf<Int>()

    private var timerRunnable: Runnable? = null
    private var reviewRunnable: Runnable? = null
    private var opponentLeftDialogShown = false

    companion object {
        const val ANSWER_NONE = "NONE"
        const val QUESTION_DURATION_MS = 10_000L
        const val REVIEW_DURATION_MS = 5_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBattleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        roomCode = intent.getStringExtra("ROOM_CODE") ?: run { finish(); return }
        mySlot = intent.getStringExtra("PLAYER_SLOT") ?: "player1"
        opponentSlot = if (mySlot == "player1") "player2" else "player1"

        setupClickListeners()
        loadPlayerNames()
        listenForOpponentScore()
        listenForOpponentLeft()

        if (mySlot == "player1") {
            loadAndShuffleQuestions()
        } else {
            waitForQuestionOrderThenListen()
        }
    }

    // ── Opponent left listener ────────────────────────────────────────────────

    private fun listenForOpponentLeft() {
        opponentLeftListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status")
                    .getValue(String::class.java) ?: return
                if (status == "abandoned" && !opponentLeftDialogShown && !isFinishing) {
                    opponentLeftDialogShown = true
                    showOpponentLeftDialog()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("rooms").child(roomCode).addValueEventListener(opponentLeftListener!!)
    }

    private fun showOpponentLeftDialog() {
        if (isFinishing) return
        DialogUtils.showAlertDialog(
            context = this,
            title = "Opponent Left",
            message = "Your opponent has left the battle.",
            iconEmoji = "🏃‍♂️", // Using a runner emoji for opponent leaving
            onConfirm = {
                endBattle() // Assuming this is your go home logic
            }
        )
    }

    private fun loadPlayerNames() {
        db.child("rooms").child(roomCode).child("players")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val myUid = snapshot.child(mySlot).child("uid").getValue(String::class.java)
                    val oppUid = snapshot.child(opponentSlot).child("uid").getValue(String::class.java)

                    val myName = snapshot.child(mySlot).child("username")
                        .getValue(String::class.java) ?: "You"
                    val oppName = snapshot.child(opponentSlot).child("username")
                        .getValue(String::class.java) ?: "Opponent"

                    binding.tvMyName.text = myName
                    binding.tvOpponentName.text = oppName

                    // Fetch and Load My Avatar
                    if (!myUid.isNullOrEmpty()) {
                        db.child("users").child(myUid).child("avatarId").addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snap: DataSnapshot) {
                                val avatarId = snap.getValue(Int::class.java) ?: 1
                                binding.ivMyAvatar.load(AvatarUtils.getAvatarUrl(avatarId)) {
                                    decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                    }

                    // Fetch and Load Opponent Avatar
                    if (!oppUid.isNullOrEmpty()) {
                        db.child("users").child(oppUid).child("avatarId").addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snap: DataSnapshot) {
                                val avatarId = snap.getValue(Int::class.java) ?: 1
                                binding.ivOpponentAvatar.load(AvatarUtils.getAvatarUrl(avatarId)) {
                                    decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun listenForOpponentScore() {
        opponentScoreListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                opponentScore = snapshot.getValue(Int::class.java) ?: 0
                binding.tvOpponentScore.text = opponentScore.toString()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("rooms").child(roomCode)
            .child("players").child(opponentSlot).child("score")
            .addValueEventListener(opponentScoreListener!!)
    }

    // ── Player 1 ─────────────────────────────────────────────────────────────

    private fun loadAndShuffleQuestions() {
        db.child("rooms").child(roomCode)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val topicId = snapshot.child("topicId")
                        .getValue(String::class.java) ?: return
                    val count = snapshot.child("questionCount")
                        .getValue(Int::class.java) ?: 10

                    db.child("questions").child(topicId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(qSnap: DataSnapshot) {
                                val all = qSnap.children
                                    .mapNotNull { it.getValue(Question::class.java) }
                                    .shuffled().take(count)
                                questions.addAll(all)
                                initAnswerLists()

                                db.child("rooms").child(roomCode)
                                    .child("questionOrder").setValue(all.map { it.id })
                                    .addOnSuccessListener {
                                        waitForPlayer2ReadyThenStart()
                                    }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // ── Player 2 ─────────────────────────────────────────────────────────────

    private fun waitForQuestionOrderThenListen() {
        db.child("rooms").child(roomCode).child("questionOrder")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        handler.postDelayed({ waitForQuestionOrderThenListen() }, 300)
                        return
                    }
                    val ids = snapshot.children
                        .mapNotNull { it.getValue(String::class.java) }

                    db.child("rooms").child(roomCode).child("topicId")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(ts: DataSnapshot) {
                                val topicId = ts.getValue(String::class.java) ?: return
                                db.child("questions").child(topicId)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(qs: DataSnapshot) {
                                            val map = qs.children
                                                .mapNotNull { it.getValue(Question::class.java) }
                                                .associateBy { it.id }
                                            ids.forEach { id ->
                                                map[id]?.let { questions.add(it) }
                                            }
                                            initAnswerLists()
                                            db.child("rooms").child(roomCode)
                                                .child("player2Ready").setValue(true)
                                            startMainListener()
                                        }
                                        override fun onCancelled(error: DatabaseError) {}
                                    })
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // ── Main listener (Player 2) ─────────────────────────────────────────────

    private fun startMainListener() {
        mainListener?.let {
            db.child("rooms").child(roomCode).removeEventListener(it)
        }
        mainListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val index = snapshot.child("currentQuestionIndex")
                    .getValue(Int::class.java) ?: return
                val phase = snapshot.child("phase")
                    .getValue(String::class.java) ?: return
                val startTime = snapshot.child("questionStartTime")
                    .getValue(Long::class.java) ?: 0L

                if (phase == "answering" && startTime <= 0L) return

                val stateKey = "$index-$phase"
                if (stateKey == lastHandledPhase) return
                lastHandledPhase = stateKey

                when (phase) {
                    "answering" -> {
                        currentIndex = index
                        inReviewPhase = false
                        myAnsweredTime = -1L
                        mySelectedOption = ANSWER_NONE
                        questionStartTime = startTime

                        displayQuestion(index)
                        resetOptionColors()
                        enableOptions(true)
                        binding.reviewPanel.visibility = View.GONE
                        binding.tvPhaseLabel.visibility = View.GONE

                        startSyncedTimer(startTime)
                    }
                    "review" -> {
                        if (!inReviewPhase) {
                            inReviewPhase = true
                            enterReviewPhase(index, startTime)
                        }
                    }
                    "finished" -> endBattle()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("rooms").child(roomCode).addValueEventListener(mainListener!!)
    }

    // ── Player 1 starts question ─────────────────────────────────────────────

    private fun startQuestion(index: Int) {
        if (index >= questions.size) {
            db.child("rooms").child(roomCode).child("phase").setValue("finished")
            handler.postDelayed({ endBattle() }, 500)
            return
        }

        currentIndex = index
        inReviewPhase = false
        myAnsweredTime = -1L
        mySelectedOption = ANSWER_NONE

        displayQuestion(index)
        resetOptionColors()
        enableOptions(true)
        binding.reviewPanel.visibility = View.GONE
        binding.tvPhaseLabel.visibility = View.GONE

        // STEP 1: Write questionStartTime FIRST alone so we can read it back fast
        db.child("rooms").child(roomCode).child("questionStartTime")
            .setValue(ServerValue.TIMESTAMP)
            .addOnSuccessListener {
                // STEP 2: Read back the real server timestamp immediately
                db.child("rooms").child(roomCode).child("questionStartTime")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot) {
                            val serverTime = snap.getValue(Long::class.java)
                                ?: System.currentTimeMillis()
                            questionStartTime = serverTime

                            // STEP 3: Now write all other fields including phase
                            // Player2's listener fires AFTER serverTime is already set
                            val updates = hashMapOf<String, Any>(
                                "currentQuestionIndex" to index,
                                "phase" to "answering",
                                "player1Answer" to ANSWER_NONE,
                                "player2Answer" to ANSWER_NONE,
                                "player1AnswerTime" to -1L,
                                "player2AnswerTime" to -1L
                            )

                            db.child("rooms").child(roomCode).updateChildren(updates)
                                .addOnSuccessListener {
                                    // Player1 starts timer with known server time
                                    startSyncedTimer(serverTime)

                                    // Schedule review
                                    reviewRunnable?.let { handler.removeCallbacks(it) }
                                    reviewRunnable = Runnable {
                                        if (!inReviewPhase && currentIndex == index) {
                                            triggerReviewPhase(index)
                                        }
                                    }
                                    val elapsed = System.currentTimeMillis() - serverTime
                                    val remaining = (QUESTION_DURATION_MS - elapsed)
                                        .coerceAtLeast(500L)
                                    handler.postDelayed(reviewRunnable!!, remaining + 200L)
                                }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            }
    }

    private fun waitForPlayer2ReadyThenStart() {
        val readyRef = db.child("rooms").child(roomCode).child("player2Ready")
        readyRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ready = snapshot.getValue(Boolean::class.java) ?: false
                if (ready) {
                    readyRef.removeEventListener(this)
                    startQuestion(0)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // ── FIX ISSUE 2: Timer fix — use floor not ceil ───────────────────────────

    private fun startSyncedTimer(serverStartTime: Long) {
        timerRunnable?.let { handler.removeCallbacks(it) }

        timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - serverStartTime
                val remaining = QUESTION_DURATION_MS - elapsed

                if (remaining <= 0) {
                    binding.progressTimer.progress = 0
                    binding.tvTimer.text = "0"
                    return
                }

                val progress = ((remaining.toFloat() / QUESTION_DURATION_MS) * 1000).toInt()
                binding.progressTimer.progress = progress

                // FIX ISSUE 1: cap display at 10, never show 11
                val displaySeconds = minOf((remaining / 1000).toInt(), 10)
                binding.tvTimer.text = displaySeconds.toString()

                // Update the timer color check
                binding.progressTimer.progressTintList =
                    if (remaining < 3000)
                        ContextCompat.getColorStateList(this@BattleActivity, R.color.difficulty_hard)
                    else
                        ContextCompat.getColorStateList(this@BattleActivity, R.color.clay_primary) // Changed to Indigo!

                handler.postDelayed(this, 100)
            }
        }
        handler.post(timerRunnable!!)
    }

    // ── Option clicks ────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.optionA.setOnClickListener { onOptionClicked("A") }
        binding.optionB.setOnClickListener { onOptionClicked("B") }
        binding.optionC.setOnClickListener { onOptionClicked("C") }
        binding.optionD.setOnClickListener { onOptionClicked("D") }
    }

    private fun onOptionClicked(option: String) {
        if (inReviewPhase || myAnsweredTime != -1L) return
        myAnsweredTime = System.currentTimeMillis()
        mySelectedOption = option
        highlightSelected(option)
        enableOptions(false)

        val timeTaken = myAnsweredTime - questionStartTime

        // Write answer AND answer time to Firebase so opponent's review can show points
        val updates = mapOf(
            "${mySlot}Answer" to option,
            "${mySlot}AnswerTime" to timeTaken
        )
        db.child("rooms").child(roomCode).updateChildren(updates)
            .addOnSuccessListener {
                if (mySlot == "player1") {
                    handler.postDelayed({ checkBothAnswered() }, 800)
                }
            }
    }

    private fun checkBothAnswered() {
        if (inReviewPhase) return
        db.child("rooms").child(roomCode)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (inReviewPhase) return
                    val p1 = snapshot.child("player1Answer")
                        .getValue(String::class.java) ?: ANSWER_NONE
                    val p2 = snapshot.child("player2Answer")
                        .getValue(String::class.java) ?: ANSWER_NONE
                    if (p1 != ANSWER_NONE && p2 != ANSWER_NONE) {
                        reviewRunnable?.let { handler.removeCallbacks(it) }
                        triggerReviewPhase(currentIndex)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun triggerReviewPhase(index: Int) {
        if (inReviewPhase || currentIndex != index) return
        inReviewPhase = true
        reviewRunnable?.let { handler.removeCallbacks(it) }
        db.child("rooms").child(roomCode).child("phase").setValue("review")
            .addOnSuccessListener {
                enterReviewPhase(index, questionStartTime)
            }
    }

    // ── Review phase ─────────────────────────────────────────────────────────

    private fun enterReviewPhase(index: Int, startTime: Long) {
        timerRunnable?.let { handler.removeCallbacks(it) }
        enableOptions(false)
        binding.progressTimer.progress = 0
        binding.tvTimer.text = "0"

        db.child("rooms").child(roomCode)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val p1Answer = snapshot.child("player1Answer")
                        .getValue(String::class.java) ?: ANSWER_NONE
                    val p2Answer = snapshot.child("player2Answer")
                        .getValue(String::class.java) ?: ANSWER_NONE
                    val p1Time = snapshot.child("player1AnswerTime")
                        .getValue(Long::class.java) ?: (QUESTION_DURATION_MS + 1)
                    val p2Time = snapshot.child("player2AnswerTime")
                        .getValue(Long::class.java) ?: (QUESTION_DURATION_MS + 1)

                    val myAnswer = if (mySlot == "player1") p1Answer else p2Answer
                    val oppAnswer = if (mySlot == "player1") p2Answer else p1Answer
                    val myTimeTaken = if (mySlot == "player1") p1Time else p2Time
                    val oppTimeTaken = if (mySlot == "player1") p2Time else p1Time

                    val correctAnswer = questions[index].correctAnswer

                    val myPoints = calculatePoints(myAnswer, correctAnswer, myTimeTaken)
                    val oppPoints = calculatePoints(oppAnswer, correctAnswer, oppTimeTaken)

                    myAnswersList[index] = myAnswer
                    oppAnswersList[index] = oppAnswer
                    myPointsList[index] = myPoints
                    oppPointsList[index] = oppPoints

                    myScore += myPoints
                    binding.tvMyScore.text = myScore.toString()
                    db.child("rooms").child(roomCode)
                        .child("players").child(mySlot).child("score").setValue(myScore)

                    showReviewUI(myAnswer, oppAnswer, correctAnswer, myPoints, oppPoints)

                    handler.postDelayed({
                        val next = index + 1
                        if (next >= questions.size) {
                            endBattle()
                        } else {
                            if (mySlot == "player1") startQuestion(next)
                        }
                    }, REVIEW_DURATION_MS)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // ── FIX SPEED BONUS: restored with your exact values ─────────────────────

    private fun calculatePoints(answer: String, correct: String, timeTakenMs: Long): Int {
        if (answer == ANSWER_NONE || answer != correct) return 0
        return when {
            timeTakenMs <= 2000 -> 10   // answered within 2 sec — full 10 pts
            timeTakenMs <= 4000 -> 8    // answered within 4 sec — 8 pts
            timeTakenMs <= 7000 -> 5    // answered within 7 sec — 5 pts
            else -> 2                   // answered before time limit — 2 pts
        }
    }

    // ── FIX: show opponent points in review panel ─────────────────────────────

    private fun showReviewUI(
        myAnswer: String, oppAnswer: String,
        correctAnswer: String, myPoints: Int, oppPoints: Int
    ) {
        highlightCorrect(correctAnswer)
        if (myAnswer != ANSWER_NONE && myAnswer != correctAnswer) highlightWrong(myAnswer)

        binding.reviewPanel.visibility = View.VISIBLE
        binding.tvPhaseLabel.visibility = View.VISIBLE
        binding.tvPhaseLabel.text = "⏱ Showing answers…"

        binding.tvMyAnswerReview.text =
            "You: ${if (myAnswer == ANSWER_NONE) "—" else myAnswer}"
        binding.tvMyAnswerReview.setTextColor(
            if (myAnswer == correctAnswer)
                ContextCompat.getColor(this, R.color.difficulty_easy)
            else ContextCompat.getColor(this, R.color.difficulty_hard)
        )

        binding.tvOppAnswerReview.text =
            "Opp: ${if (oppAnswer == ANSWER_NONE) "—" else oppAnswer}"
        binding.tvOppAnswerReview.setTextColor(
            if (oppAnswer == correctAnswer)
                ContextCompat.getColor(this, R.color.difficulty_easy)
            else ContextCompat.getColor(this, R.color.difficulty_hard)
        )

        // FIX: show opponent points too
        binding.tvMyPointsEarned.text = if (myPoints > 0) "+$myPoints pts" else "No points"
        binding.tvMyPointsEarned.setTextColor(
            if (myPoints > 0)
                ContextCompat.getColor(this, R.color.difficulty_easy)
            else ContextCompat.getColor(this, R.color.text_secondary)
        )

        binding.tvOppPointsEarned.text = if (oppPoints > 0) "+$oppPoints pts" else "No points"
        binding.tvOppPointsEarned.setTextColor(
            if (oppPoints > 0)
                ContextCompat.getColor(this, R.color.difficulty_easy)
            else ContextCompat.getColor(this, R.color.text_secondary)
        )
    }

    private fun highlightSelected(option: String) =
        getOptionView(option)?.setBackgroundResource(R.drawable.option_selected_bg)
    private fun highlightCorrect(option: String) =
        getOptionView(option)?.setBackgroundResource(R.drawable.option_correct_bg)
    private fun highlightWrong(option: String) =
        getOptionView(option)?.setBackgroundResource(R.drawable.option_wrong_bg)
    private fun resetOptionColors() = listOf("A", "B", "C", "D").forEach {
        getOptionView(it)?.setBackgroundResource(R.drawable.option_default_bg)
    }
    private fun enableOptions(enabled: Boolean) {
        listOf(binding.optionA, binding.optionB, binding.optionC, binding.optionD).forEach {
            it.isClickable = enabled
            it.alpha = if (enabled) 1.0f else 0.85f
        }
    }
    private fun getOptionView(option: String) = when (option) {
        "A" -> binding.optionA; "B" -> binding.optionB
        "C" -> binding.optionC; "D" -> binding.optionD
        else -> null
    }

    private fun initAnswerLists() {
        repeat(questions.size) {
            myAnswersList.add(ANSWER_NONE)
            oppAnswersList.add(ANSWER_NONE)
            myPointsList.add(0)
            oppPointsList.add(0)
        }
    }

    private fun displayQuestion(index: Int) {
        if (index >= questions.size) return
        val q = questions[index]
        binding.tvQuestion.text = q.question
        binding.tvOptionA.text = q.optionA
        binding.tvOptionB.text = q.optionB
        binding.tvOptionC.text = q.optionC
        binding.tvOptionD.text = q.optionD
        binding.tvQuestionNumber.text = "${index + 1}/${questions.size}"
    }

    private fun endBattle() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        reviewRunnable?.let { handler.removeCallbacks(it) }
        mainListener?.let {
            db.child("rooms").child(roomCode).removeEventListener(it)
        }
        opponentScoreListener?.let {
            db.child("rooms").child(roomCode)
                .child("players").child(opponentSlot).child("score")
                .removeEventListener(it)
        }
        opponentLeftListener?.let {
            db.child("rooms").child(roomCode).removeEventListener(it)
        }
        if (mySlot == "player1") {
            db.child("rooms").child(roomCode).child("status").setValue("finished")
        }

        db.child("rooms").child(roomCode)
            .child("players").child(opponentSlot).child("score")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val finalOppScore = snapshot.getValue(Int::class.java) ?: opponentScore
                    startActivity(
                        Intent(this@BattleActivity, BattleResultActivity::class.java).apply {
                            putExtra("ROOM_CODE", roomCode)
                            putExtra("MY_SLOT", mySlot)
                            putExtra("MY_SCORE", myScore)
                            putExtra("OPP_SCORE", finalOppScore)
                            putStringArrayListExtra("MY_ANSWERS", ArrayList(myAnswersList))
                            putStringArrayListExtra("OPP_ANSWERS", ArrayList(oppAnswersList))
                            putStringArrayListExtra("QUESTION_TEXTS",
                                ArrayList(questions.map { it.question }))
                            putStringArrayListExtra("CORRECT_ANSWERS",
                                ArrayList(questions.map { it.correctAnswer }))
                            putStringArrayListExtra("OPTIONS_A",
                                ArrayList(questions.map { it.optionA }))
                            putStringArrayListExtra("OPTIONS_B",
                                ArrayList(questions.map { it.optionB }))
                            putStringArrayListExtra("OPTIONS_C",
                                ArrayList(questions.map { it.optionC }))
                            putStringArrayListExtra("OPTIONS_D",
                                ArrayList(questions.map { it.optionD }))
                            putIntegerArrayListExtra("MY_POINTS", ArrayList(myPointsList))
                        }
                    )
                    finish()
                }
                override fun onCancelled(error: DatabaseError) { finish() }
            })
    }

    // ── FIX ISSUE 6: set abandoned status when back pressed ──────────────────

    override fun onBackPressed() {
        // Set room status to abandoned so opponent sees dialog
        db.child("rooms").child(roomCode).child("status").setValue("abandoned")
        endBattle()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerRunnable?.let { handler.removeCallbacks(it) }
        reviewRunnable?.let { handler.removeCallbacks(it) }
        mainListener?.let {
            db.child("rooms").child(roomCode).removeEventListener(it)
        }
        opponentScoreListener?.let {
            db.child("rooms").child(roomCode)
                .child("players").child(opponentSlot).child("score")
                .removeEventListener(it)
        }
        opponentLeftListener?.let {
            db.child("rooms").child(roomCode).removeEventListener(it)
        }
    }
}