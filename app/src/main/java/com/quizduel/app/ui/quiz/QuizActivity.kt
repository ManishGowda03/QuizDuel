package com.quizduel.app.ui.quiz

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.quizduel.app.R
import com.quizduel.app.data.model.Question
import com.quizduel.app.data.model.QuizResult
import com.quizduel.app.databinding.ActivityQuizBinding
import com.quizduel.app.ui.result.ResultActivity
import kotlin.math.ceil

class QuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizBinding

    private val questions = mutableListOf<Question>()
    private val results = mutableListOf<QuizResult>()

    private var currentIndex = 0
    private var score = 0
    private var selectedOption = ""
    private var correctAnswer = ""
    private var timer: CountDownTimer? = null
    private var timeLeft = 100

    private lateinit var topicId: String
    private lateinit var topicName: String

    companion object {
        const val EXTRA_TOPIC_ID = "topic_id"
        const val EXTRA_TOPIC_NAME = "topic_name"
        private const val TIMER_DURATION = 15000L // 15 seconds
        private const val MAX_QUESTIONS = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make the status bar match the cool background
        window.statusBarColor = getColor(R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        topicId = intent.getStringExtra(EXTRA_TOPIC_ID) ?: ""
        topicName = intent.getStringExtra(EXTRA_TOPIC_NAME) ?: ""

        if (topicId.isEmpty()) {
            Toast.makeText(this, "Topic not found!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener { onBackPressed() }
        binding.btnSubmit.setOnClickListener { submitAnswer() }

        setupOptionClickListeners()
        loadQuestions()
    }

    private fun loadQuestions() {
        FirebaseDatabase.getInstance().getReference("questions")
            .child(topicId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    questions.clear()
                    for (questionSnap in snapshot.children) {
                        val question = questionSnap.getValue(Question::class.java)
                        if (question != null) questions.add(question)
                    }

                    if (questions.isEmpty()) {
                        Toast.makeText(this@QuizActivity, "No questions available!", Toast.LENGTH_SHORT).show()
                        finish()
                        return
                    }

                    questions.shuffle()
                    if (questions.size > MAX_QUESTIONS) {
                        val limited = questions.take(MAX_QUESTIONS)
                        questions.clear()
                        questions.addAll(limited)
                    }

                    showQuestion()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@QuizActivity, "Failed to load questions!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            })
    }

    private fun showQuestion() {
        if (currentIndex >= questions.size) {
            finishQuiz()
            return
        }

        val question = questions[currentIndex]
        correctAnswer = question.correctAnswer
        selectedOption = ""

        resetOptionsUI()
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.alpha = 0.5f

        binding.tvQuestionCounter.text = "Question ${currentIndex + 1}/${questions.size}"
        binding.tvScore.text = "Score: $score"
        binding.tvQuestion.text = question.question

        binding.tvOptionA.text = question.optionA
        binding.tvOptionB.text = question.optionB
        binding.tvOptionC.text = question.optionC
        binding.tvOptionD.text = question.optionD

        // Reset Timer Colors to Indigo initially
        val indigoColor = getColor(R.color.clay_primary)
        binding.tvTimerText.text = "15s"
        binding.tvTimerText.setTextColor(indigoColor)
        binding.timerProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(indigoColor)

        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()
        timeLeft = 100
        binding.timerProgressBar.progress = 100

        val indigoColor = getColor(R.color.clay_primary)
        val dimOrangeColor = Color.parseColor("#FFB74D") // Soft pastel orange
        val dimRedColor = Color.parseColor("#EF9A9A")    // Soft pastel red

        timer = object : CountDownTimer(TIMER_DURATION, 50) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = (millisUntilFinished * 100 / TIMER_DURATION).toInt()
                binding.timerProgressBar.progress = timeLeft

                // Calculate seconds left and update text
                val secondsLeft = ceil(millisUntilFinished / 1000.0).toInt()
                binding.tvTimerText.text = "${secondsLeft}s"

                // Change colors based on time remaining
                val currentColor = when {
                    secondsLeft <= 2 -> dimRedColor
                    secondsLeft <= 5 -> dimOrangeColor
                    else -> indigoColor
                }

                binding.tvTimerText.setTextColor(currentColor)
                binding.timerProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(currentColor)
            }

            override fun onFinish() {
                binding.timerProgressBar.progress = 0
                binding.tvTimerText.text = "0s"
                recordResult(isCorrect = false, userAnswer = "No Answer")
                moveToNext()
            }
        }.start()
    }

    private fun setupOptionClickListeners() {
        binding.cardOptionA.setOnClickListener { selectOption("A", binding.cardOptionA) }
        binding.cardOptionB.setOnClickListener { selectOption("B", binding.cardOptionB) }
        binding.cardOptionC.setOnClickListener { selectOption("C", binding.cardOptionC) }
        binding.cardOptionD.setOnClickListener { selectOption("D", binding.cardOptionD) }
    }

    private fun selectOption(option: String, selectedCard: CardView) {
        if (binding.btnSubmit.tag == "submitted") return

        selectedOption = option
        resetOptionsUI()

        // Apply Claymorphism Selected State (Light Indigo background)
        selectedCard.setCardBackgroundColor(Color.parseColor("#E8EAFF"))

        val indigoColor = getColor(R.color.clay_primary)
        val whiteColor = getColor(R.color.clay_white)

        when (option) {
            "A" -> {
                binding.tvOptionA.setTextColor(indigoColor)
                binding.tvOptionALetter.backgroundTintList = android.content.res.ColorStateList.valueOf(indigoColor)
                binding.tvOptionALetter.setTextColor(whiteColor)
            }
            "B" -> {
                binding.tvOptionB.setTextColor(indigoColor)
                binding.tvOptionBLetter.backgroundTintList = android.content.res.ColorStateList.valueOf(indigoColor)
                binding.tvOptionBLetter.setTextColor(whiteColor)
            }
            "C" -> {
                binding.tvOptionC.setTextColor(indigoColor)
                binding.tvOptionCLetter.backgroundTintList = android.content.res.ColorStateList.valueOf(indigoColor)
                binding.tvOptionCLetter.setTextColor(whiteColor)
            }
            "D" -> {
                binding.tvOptionD.setTextColor(indigoColor)
                binding.tvOptionDLetter.backgroundTintList = android.content.res.ColorStateList.valueOf(indigoColor)
                binding.tvOptionDLetter.setTextColor(whiteColor)
            }
        }

        binding.btnSubmit.isEnabled = true
        binding.btnSubmit.alpha = 1.0f
    }

    private fun submitAnswer() {
        if (selectedOption.isEmpty()) return
        timer?.cancel()

        binding.btnSubmit.tag = "submitted"
        binding.btnSubmit.isEnabled = false

        val isCorrect = selectedOption == correctAnswer

        // EXACTLY 10 points for correct, 0 for wrong. No speed bonus.
        if (isCorrect) {
            score += 10
            binding.tvScore.text = "Score: $score"
        }

        showAnswerFeedback(isCorrect)

        val question = questions[currentIndex]
        val userAnswerText = when (selectedOption) {
            "A" -> "A) ${question.optionA}"
            "B" -> "B) ${question.optionB}"
            "C" -> "C) ${question.optionC}"
            "D" -> "D) ${question.optionD}"
            else -> selectedOption
        }
        recordResult(isCorrect, userAnswerText)

        binding.root.postDelayed({ moveToNext() }, 1500) // Give user 1.5s to see the right/wrong colors
    }

    private fun showAnswerFeedback(isCorrect: Boolean) {
        // Soft Pastel/Dim Colors for Feedback
        val dimGreen = Color.parseColor("#D4EDDA") // Soft Mint Green
        val dimGreenText = Color.parseColor("#155724")

        val dimRed = Color.parseColor("#F8D7DA") // Soft Pastel Red
        val dimRedText = Color.parseColor("#721C24")

        // 1. Highlight Correct Answer in Dim Green
        val correctCard = when (correctAnswer) {
            "A" -> { binding.tvOptionA.setTextColor(dimGreenText); binding.cardOptionA }
            "B" -> { binding.tvOptionB.setTextColor(dimGreenText); binding.cardOptionB }
            "C" -> { binding.tvOptionC.setTextColor(dimGreenText); binding.cardOptionC }
            "D" -> { binding.tvOptionD.setTextColor(dimGreenText); binding.cardOptionD }
            else -> binding.cardOptionA
        }
        correctCard.setCardBackgroundColor(dimGreen)

        // 2. If wrong, highlight Selected Answer in Dim Red
        if (!isCorrect) {
            val wrongCard = when (selectedOption) {
                "A" -> { binding.tvOptionA.setTextColor(dimRedText); binding.cardOptionA }
                "B" -> { binding.tvOptionB.setTextColor(dimRedText); binding.cardOptionB }
                "C" -> { binding.tvOptionC.setTextColor(dimRedText); binding.cardOptionC }
                "D" -> { binding.tvOptionD.setTextColor(dimRedText); binding.cardOptionD }
                else -> binding.cardOptionA
            }
            wrongCard.setCardBackgroundColor(dimRed)
        }
    }

    private fun recordResult(isCorrect: Boolean, userAnswer: String) {
        val question = questions[currentIndex]
        val correctText = when (correctAnswer) {
            "A" -> "A) ${question.optionA}"
            "B" -> "B) ${question.optionB}"
            "C" -> "C) ${question.optionC}"
            "D" -> "D) ${question.optionD}"
            else -> correctAnswer
        }
        results.add(
            QuizResult(
                question = question.question,
                userAnswer = userAnswer,
                correctAnswer = correctText,
                isCorrect = isCorrect,
                optionA = question.optionA,
                optionB = question.optionB,
                optionC = question.optionC,
                optionD = question.optionD
            )
        )
    }

    private fun moveToNext() {
        currentIndex++
        binding.btnSubmit.tag = null
        showQuestion()
    }

    private fun resetOptionsUI() {
        val whiteColor = getColor(R.color.clay_white)
        val darkTextColor = getColor(R.color.clay_text_dark)
        val mutedTextColor = getColor(R.color.clay_text_muted)
        val lightGrayBg = android.content.res.ColorStateList.valueOf(Color.parseColor("#F0F0F0"))

        // Reset Card Backgrounds
        listOf(binding.cardOptionA, binding.cardOptionB, binding.cardOptionC, binding.cardOptionD).forEach {
            it.setCardBackgroundColor(whiteColor)
        }

        // Reset Main Text
        listOf(binding.tvOptionA, binding.tvOptionB, binding.tvOptionC, binding.tvOptionD).forEach {
            it.setTextColor(darkTextColor)
        }

        // Reset the A/B/C/D Letters
        listOf(binding.tvOptionALetter, binding.tvOptionBLetter, binding.tvOptionCLetter, binding.tvOptionDLetter).forEach {
            it.backgroundTintList = lightGrayBg
            it.setTextColor(mutedTextColor)
        }
    }

    private fun finishQuiz() {
        timer?.cancel()
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_SCORE, score)
            putExtra(ResultActivity.EXTRA_TOPIC_NAME, topicName)
            putExtra(ResultActivity.EXTRA_TOPIC_ID, topicId)
            putExtra(ResultActivity.EXTRA_CORRECT, results.count { it.isCorrect })
            putExtra(ResultActivity.EXTRA_WRONG, results.count { !it.isCorrect })
            putExtra(ResultActivity.EXTRA_TOTAL, questions.size)
            putParcelableArrayListExtra(
                ResultActivity.EXTRA_RESULTS,
                ArrayList(results.map { result ->
                    Bundle().apply {
                        putString("question", result.question)
                        putString("userAnswer", result.userAnswer)
                        putString("correctAnswer", result.correctAnswer)
                        putBoolean("isCorrect", result.isCorrect)
                    }
                })
            )
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}