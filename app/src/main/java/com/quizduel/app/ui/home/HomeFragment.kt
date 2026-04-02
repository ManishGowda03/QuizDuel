package com.quizduel.app.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.quizduel.app.data.model.Topic
import com.quizduel.app.databinding.FragmentHomeBinding
import com.quizduel.app.ui.quiz.QuizActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var recentQuizAdapter: RecentQuizAdapter

    private val allTopics = mutableListOf<Topic>()
    private var selectedCategory = "All"
    private var currentSearchQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupSearch()
        loadTopicsFromFirebase()
        loadDailyChallenge()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString().lowercase()
                filterQuizzes()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadDailyChallenge() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val dailyRef = FirebaseDatabase.getInstance().getReference("dailyChallenge")

        dailyRef.get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val storedDate = snapshot.child("date").getValue(String::class.java) ?: ""
            if (storedDate == today) {
                val topicId = snapshot.child("topicId").getValue(String::class.java) ?: ""
                val topicName = snapshot.child("topicName").getValue(String::class.java) ?: ""

                val countObj = snapshot.child("questionCount").value
                val questionCount = when (countObj) {
                    is Long -> countObj.toInt()
                    is String -> countObj.toIntOrNull() ?: 0
                    else -> 0
                }

                updateDailyChallengeUI(topicId, topicName, questionCount, today)
            } else {
                pickNewDailyChallenge(today)
            }
        }
    }

    private fun pickNewDailyChallenge(today: String) {
        FirebaseDatabase.getInstance().getReference("topics").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val topics = mutableListOf<Topic>()
            for (topicSnap in snapshot.children) {
                val topic = topicSnap.getValue(Topic::class.java)
                if (topic != null && topic.isActive) topics.add(topic)
            }
            if (topics.isEmpty()) return@addOnSuccessListener
            val randomTopic = topics.random()

            val dailyMap = mapOf(
                "topicId" to randomTopic.id,
                "topicName" to randomTopic.name,
                "questionCount" to randomTopic.questionCount,
                "date" to today
            )
            FirebaseDatabase.getInstance().getReference("dailyChallenge").setValue(dailyMap)
            updateDailyChallengeUI(randomTopic.id, randomTopic.name, randomTopic.questionCount, today)
        }
    }

    private fun updateDailyChallengeUI(topicId: String, topicName: String, questionCount: Int, today: String) {
        if (_binding == null) return
        binding.tvDailyTopic.text = topicName
        binding.tvDailyQuestionCount.text = "$questionCount Questions"

        // Check if attempted today
        val prefs = requireContext().getSharedPreferences("QuizDuelPrefs", Context.MODE_PRIVATE)
        val attemptedDate = prefs.getString("daily_attempted_date", "")

        if (attemptedDate == today) {
            binding.btnPlayDaily.visibility = View.GONE
            binding.tvAttempted.visibility = View.VISIBLE
        } else {
            binding.btnPlayDaily.visibility = View.VISIBLE
            binding.tvAttempted.visibility = View.GONE

            binding.btnPlayDaily.setOnClickListener {
                // Simply call the custom dialog. The dialog handles the rest!
                showQuizConfirmationDialog(topicId, topicName, isDaily = true, todayDate = today)
            }
        }
    }

    private fun setupAdapters() {
        val categories = listOf(
            Topic(id = "all", name = "All", icon = "🌐"),
            Topic(id = "programming", name = "Programming", icon = "💻"),
            Topic(id = "database", name = "Database", icon = "🗄️"),
            Topic(id = "interview", name = "Interview", icon = "🎯")
        )

        categoryAdapter = CategoryAdapter(categories) { category ->
            selectedCategory = category.name
            filterQuizzes()
        }
        binding.rvCategories.adapter = categoryAdapter

        recentQuizAdapter = RecentQuizAdapter(emptyList()) { topic ->
            // Replace ugly AlertDialog with our clean custom dialog!
            showQuizConfirmationDialog(topic.id, topic.name, isDaily = false)
        }
        binding.rvRecentQuizzes.adapter = recentQuizAdapter
    }

    private fun loadTopicsFromFirebase() {
        FirebaseDatabase.getInstance().getReference("topics").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allTopics.clear()
                for (topicSnap in snapshot.children) {
                    val topic = topicSnap.getValue(Topic::class.java)
                    if (topic != null && topic.isActive) allTopics.add(topic)
                }
                filterQuizzes()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun filterQuizzes() {
        // 1. Filter by Category
        var filtered = if (selectedCategory == "All") {
            allTopics
        } else {
            allTopics.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }

        // 2. Filter by Search Query
        if (currentSearchQuery.isNotEmpty()) {
            filtered = filtered.filter { it.name.lowercase().contains(currentSearchQuery) }
        }

        recentQuizAdapter.updateList(filtered)
    }

    private fun showQuizConfirmationDialog(topicId: String, topicName: String, isDaily: Boolean, todayDate: String = "") {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(com.quizduel.app.R.layout.dialog_confirm_quiz)

        // Make background transparent so our rounded corners show
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val tvTitle = dialog.findViewById<android.widget.TextView>(com.quizduel.app.R.id.tvDialogTitle)
        val btnCancel = dialog.findViewById<android.widget.TextView>(com.quizduel.app.R.id.btnCancel)
        val btnStart = dialog.findViewById<com.google.android.material.button.MaterialButton>(com.quizduel.app.R.id.btnStartQuiz)

        tvTitle.text = "Topic : $topicName"

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnStart.setOnClickListener {
            dialog.dismiss()

            if (isDaily) {
                val prefs = requireContext().getSharedPreferences("QuizDuelPrefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("daily_attempted_date", todayDate).apply()
            }

            val intent = Intent(requireContext(), QuizActivity::class.java).apply {
                putExtra(QuizActivity.EXTRA_TOPIC_ID, topicId)
                putExtra(QuizActivity.EXTRA_TOPIC_NAME, topicName)
            }
            startActivity(intent)
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}