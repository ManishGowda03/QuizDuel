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
import com.quizduel.app.databinding.ActivityBattleReviewBinding

class BattleReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBattleReviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBattleReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        binding.btnBack.setOnClickListener { finish() }

        val myAnswers = intent.getStringArrayListExtra("MY_ANSWERS") ?: arrayListOf()
        val oppAnswers = intent.getStringArrayListExtra("OPP_ANSWERS") ?: arrayListOf()
        val questionTexts = intent.getStringArrayListExtra("QUESTION_TEXTS") ?: arrayListOf()
        val correctAnswers = intent.getStringArrayListExtra("CORRECT_ANSWERS") ?: arrayListOf()
        val optionsA = intent.getStringArrayListExtra("OPTIONS_A") ?: arrayListOf()
        val optionsB = intent.getStringArrayListExtra("OPTIONS_B") ?: arrayListOf()
        val optionsC = intent.getStringArrayListExtra("OPTIONS_C") ?: arrayListOf()
        val optionsD = intent.getStringArrayListExtra("OPTIONS_D") ?: arrayListOf()

        binding.rvReview.layoutManager = LinearLayoutManager(this)
        binding.rvReview.adapter = ReviewAdapter(
            questionTexts, correctAnswers, myAnswers, oppAnswers,
            optionsA, optionsB, optionsC, optionsD
        )
    }

    inner class ReviewAdapter(
        private val questions: List<String>,
        private val correctAnswers: List<String>,
        private val myAnswers: List<String>,
        private val oppAnswers: List<String>,
        private val optionsA: List<String>,
        private val optionsB: List<String>,
        private val optionsC: List<String>,
        private val optionsD: List<String>
    ) : RecyclerView.Adapter<ReviewAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvQuestion: TextView = view.findViewById(R.id.tvQuestionText)
            val tvCorrect: TextView = view.findViewById(R.id.tvCorrectAnswer)
            val tvMy: TextView = view.findViewById(R.id.tvMyAnswer)
            val tvOpp: TextView = view.findViewById(R.id.tvOppAnswer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_battle_result_question, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.tvQuestion.text = "${position + 1}. ${questions[position]}"

            val correct = correctAnswers[position]
            val correctText = when (correct) {
                "A" -> optionsA.getOrElse(position) { "" }
                "B" -> optionsB.getOrElse(position) { "" }
                "C" -> optionsC.getOrElse(position) { "" }
                "D" -> optionsD.getOrElse(position) { "" }
                else -> ""
            }
            holder.tvCorrect.text = "✓ $correct: $correctText"

            val myAns = myAnswers.getOrElse(position) { BattleActivity.ANSWER_NONE }
            val oppAns = oppAnswers.getOrElse(position) { BattleActivity.ANSWER_NONE }

            holder.tvMy.text = "You: ${if (myAns == BattleActivity.ANSWER_NONE) "—" else myAns}"
            holder.tvMy.setTextColor(
                if (myAns == correct)
                    ContextCompat.getColor(holder.itemView.context, R.color.difficulty_easy)
                else ContextCompat.getColor(holder.itemView.context, R.color.difficulty_hard)
            )

            holder.tvOpp.text = "Opp: ${if (oppAns == BattleActivity.ANSWER_NONE) "—" else oppAns}"
            holder.tvOpp.setTextColor(
                if (oppAns == correct)
                    ContextCompat.getColor(holder.itemView.context, R.color.difficulty_easy)
                else ContextCompat.getColor(holder.itemView.context, R.color.difficulty_hard)
            )
        }

        override fun getItemCount() = questions.size
    }
}