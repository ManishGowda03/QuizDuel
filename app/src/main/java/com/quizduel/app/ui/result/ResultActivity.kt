package com.quizduel.app.ui.result

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.quizduel.app.data.model.QuizResult
import com.quizduel.app.databinding.ActivityResultBinding
import com.quizduel.app.ui.home.HomeActivity
import com.quizduel.app.ui.quiz.QuizActivity

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var resultAdapter: ResultAdapter

    companion object {
        const val EXTRA_SCORE = "extra_score"
        const val EXTRA_TOPIC_NAME = "extra_topic_name"
        const val EXTRA_CORRECT = "extra_correct"
        const val EXTRA_WRONG = "extra_wrong"
        const val EXTRA_TOTAL = "extra_total"
        const val EXTRA_RESULTS = "extra_results"
        const val EXTRA_TOPIC_ID = "extra_topic_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make the status bar match the cool clay background
        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        val score = intent.getIntExtra(EXTRA_SCORE, 0)
        val topicName = intent.getStringExtra(EXTRA_TOPIC_NAME) ?: ""
        val correct = intent.getIntExtra(EXTRA_CORRECT, 0)
        val wrong = intent.getIntExtra(EXTRA_WRONG, 0)
        val topicId = intent.getStringExtra(EXTRA_TOPIC_ID) ?: ""
        val resultBundles = intent.getParcelableArrayListExtra<Bundle>(EXTRA_RESULTS) ?: arrayListOf()

        // Set Hero Stats
        binding.tvScore.text = "$score pts"
        binding.tvCorrectCount.text = "✓ $correct Correct"
        binding.tvWrongCount.text = "✕ $wrong Wrong"

        // Build results list
        val results = resultBundles.map { bundle ->
            QuizResult(
                question = bundle.getString("question") ?: "",
                userAnswer = bundle.getString("userAnswer") ?: "",
                correctAnswer = bundle.getString("correctAnswer") ?: "",
                isCorrect = bundle.getBoolean("isCorrect")
            )
        }

        // Setup breakdown RecyclerView
        resultAdapter = ResultAdapter(results)
        binding.rvBreakdown.adapter = resultAdapter

        // Click Listeners
        binding.btnBack.setOnClickListener { navigateHome() }
        binding.btnGoHome.setOnClickListener { navigateHome() }

        binding.btnPlayAgain.setOnClickListener {
            val intent = Intent(this, QuizActivity::class.java).apply {
                putExtra(QuizActivity.EXTRA_TOPIC_ID, topicId)
                putExtra(QuizActivity.EXTRA_TOPIC_NAME, topicName)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun navigateHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }
}