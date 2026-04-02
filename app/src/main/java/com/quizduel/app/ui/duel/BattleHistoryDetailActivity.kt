package com.quizduel.app.ui.duel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quizduel.app.R
import com.quizduel.app.databinding.ActivityBattleHistoryDetailBinding

class BattleHistoryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBattleHistoryDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBattleHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        binding.btnBack.setOnClickListener { finish() }

        val opponentName = intent.getStringExtra("OPPONENT_NAME") ?: "Opponent"
        val myName = intent.getStringExtra("MY_NAME") ?: "You"
        val myScore = intent.getIntExtra("MY_SCORE", 0)
        val oppScore = intent.getIntExtra("OPP_SCORE", 0)
        val result = intent.getStringExtra("RESULT") ?: "draw"
        val topicName = intent.getStringExtra("TOPIC_NAME") ?: ""

        val qTexts = intent.getStringArrayListExtra("Q_TEXTS") ?: arrayListOf()
        val qCorrect = intent.getStringArrayListExtra("Q_CORRECT") ?: arrayListOf()
        val qMyAnswers = intent.getStringArrayListExtra("Q_MY_ANSWERS") ?: arrayListOf()
        val qOppAnswers = intent.getStringArrayListExtra("Q_OPP_ANSWERS") ?: arrayListOf()
        val qOptionsA = intent.getStringArrayListExtra("Q_OPTIONS_A") ?: arrayListOf()
        val qOptionsB = intent.getStringArrayListExtra("Q_OPTIONS_B") ?: arrayListOf()
        val qOptionsC = intent.getStringArrayListExtra("Q_OPTIONS_C") ?: arrayListOf()
        val qOptionsD = intent.getStringArrayListExtra("Q_OPTIONS_D") ?: arrayListOf()

        // Header
        binding.tvDetailTitle.text = "vs $opponentName"
        binding.tvDetailSubtitle.text = topicName

        // Score summary
        binding.tvDetailMyName.text = myName
        binding.tvDetailMyScore.text = myScore.toString()
        binding.tvDetailOppName.text = opponentName
        binding.tvDetailOppScore.text = oppScore.toString()

        val resultText = when (result) {
            "win" -> "🏆 WIN"
            "loss" -> "😔 LOSS"
            else -> "🤝 DRAW"
        }
        binding.tvDetailResult.text = resultText
        binding.tvDetailResult.setBackgroundResource(
            if (result == "win") R.drawable.circle_red_bg else R.drawable.circle_gray_bg
        )

        // Questions list
        val adapter = HistoryQuestionAdapter(
            qTexts, qCorrect, qMyAnswers, qOppAnswers,
            qOptionsA, qOptionsB, qOptionsC, qOptionsD
        )
        binding.rvQuestions.layoutManager = LinearLayoutManager(this)
        binding.rvQuestions.adapter = adapter
    }

    // ── Inner adapter reusing item_battle_result_question.xml ────────────────
    inner class HistoryQuestionAdapter(
        private val texts: List<String>,
        private val correct: List<String>,
        private val myAnswers: List<String>,
        private val oppAnswers: List<String>,
        private val optA: List<String>,
        private val optB: List<String>,
        private val optC: List<String>,
        private val optD: List<String>
    ) : RecyclerView.Adapter<HistoryQuestionAdapter.QViewHolder>() {

        inner class QViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvQuestion: TextView = itemView.findViewById(R.id.tvQuestionText)
            val tvCorrect: TextView = itemView.findViewById(R.id.tvCorrectAnswer)
            val tvMyAnswer: TextView = itemView.findViewById(R.id.tvMyAnswer)
            val tvOppAnswer: TextView = itemView.findViewById(R.id.tvOppAnswer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_battle_result_question, parent, false)
            return QViewHolder(view)
        }

        override fun onBindViewHolder(holder: QViewHolder, position: Int) {
            val q = texts.getOrNull(position) ?: return
            val correctAns = correct.getOrNull(position) ?: ""
            val myAns = myAnswers.getOrNull(position) ?: "—"
            val oppAns = oppAnswers.getOrNull(position) ?: "—"

            holder.tvQuestion.text = "${position + 1}. $q"

            // Show correct answer with full option text
            val correctText = when (correctAns) {
                "A" -> optA.getOrNull(position) ?: ""
                "B" -> optB.getOrNull(position) ?: ""
                "C" -> optC.getOrNull(position) ?: ""
                "D" -> optD.getOrNull(position) ?: ""
                else -> ""
            }
            holder.tvCorrect.text = "✓ $correctAns: $correctText"

            // My answer
            val myCorrect = myAns == correctAns
            holder.tvMyAnswer.text = "You: $myAns"
            holder.tvMyAnswer.setTextColor(
                if (myCorrect)
                    ContextCompat.getColor(this@BattleHistoryDetailActivity, R.color.difficulty_easy)
                else
                    ContextCompat.getColor(this@BattleHistoryDetailActivity, R.color.difficulty_hard)
            )

            // Opponent answer
            val oppCorrect = oppAns == correctAns
            holder.tvOppAnswer.text = "Opp: $oppAns"
            holder.tvOppAnswer.setTextColor(
                if (oppCorrect)
                    ContextCompat.getColor(this@BattleHistoryDetailActivity, R.color.difficulty_easy)
                else
                    ContextCompat.getColor(this@BattleHistoryDetailActivity, R.color.difficulty_hard)
            )
        }

        override fun getItemCount() = texts.size
    }
}