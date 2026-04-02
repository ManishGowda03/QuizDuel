package com.quizduel.app.ui.admin

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.quizduel.app.data.model.Question
import com.quizduel.app.data.model.Topic
import com.quizduel.app.databinding.DialogAddQuestionBinding
import com.quizduel.app.databinding.DialogBulkAddBinding
import com.quizduel.app.databinding.FragmentAdminQuestionsBinding
import org.json.JSONArray
import org.json.JSONObject

class AdminQuestionsFragment : Fragment() {

    private var _binding: FragmentAdminQuestionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var questionAdapter: AdminQuestionAdapter
    private val questions = mutableListOf<Question>()
    private val topics = mutableListOf<Topic>()
    private var selectedTopicId = ""
    private var editingQuestionId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminQuestionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapter()
        loadTopics()

        binding.fabAddQuestion.setOnClickListener {
            if (selectedTopicId.isEmpty()) {
                Toast.makeText(requireContext(), "Please select a topic first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAddQuestionDialog(null)
        }

        binding.fabBulkAdd.setOnClickListener {
            if (selectedTopicId.isEmpty()) {
                Toast.makeText(requireContext(), "Please select a topic first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showBulkAddDialog()
        }
    }

    private fun setupAdapter() {
        questionAdapter = AdminQuestionAdapter(
            questions,
            onEdit = { question -> showAddQuestionDialog(question) },
            onDelete = { question -> confirmDelete(question) }
        )
        binding.rvQuestions.adapter = questionAdapter
    }

    private fun loadTopics() {
        FirebaseDatabase.getInstance().getReference("topics")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    topics.clear()
                    for (topicSnap in snapshot.children) {
                        val topic = topicSnap.getValue(Topic::class.java)
                        if (topic != null) topics.add(topic)
                    }
                    setupTopicSearch() // Call the new function here!
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupTopicSearch() {
        val topicNames = topics.map { it.name }

        // Use the standard dropdown array adapter
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            topicNames
        )
        binding.actvTopicSearch.setAdapter(adapter)

        // When the admin clicks a filtered result
        binding.actvTopicSearch.setOnItemClickListener { _, _, position, _ ->
            val selectedName = adapter.getItem(position)
            val selectedTopic = topics.find { it.name == selectedName }

            selectedTopic?.let {
                selectedTopicId = it.id
                loadQuestions(selectedTopicId)
            }
        }

        // Auto-select the last added topic so the screen isn't empty!
        if (topics.isNotEmpty()) {
            val latestTopic = topics.last() // Grabs the newest topic from Firebase
            selectedTopicId = latestTopic.id

            // Set the text in the search bar (false prevents it from flashing the dropdown)
            binding.actvTopicSearch.setText(latestTopic.name, false)

            loadQuestions(selectedTopicId)
        }
    }

    private fun loadQuestions(topicId: String) {
        FirebaseDatabase.getInstance().getReference("questions")
            .child(topicId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    questions.clear()
                    for (questionSnap in snapshot.children) {
                        val question = questionSnap.getValue(Question::class.java)
                        if (question != null) questions.add(question)
                    }
                    questionAdapter.notifyDataSetChanged()
                    binding.tvQuestionCount.text = "${questions.size} Questions"
                    binding.layoutEmpty.visibility =
                        if (questions.isEmpty()) View.VISIBLE else View.GONE
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun showAddQuestionDialog(existingQuestion: Question?) {
        val dialogView = DialogAddQuestionBinding.inflate(layoutInflater)
        editingQuestionId = existingQuestion?.id

        existingQuestion?.let { q ->
            dialogView.tvDialogTitle.text = "Edit Question"
            dialogView.etQuestion.setText(q.question)
            dialogView.etOptionA.setText(q.optionA)
            dialogView.etOptionB.setText(q.optionB)
            dialogView.etOptionC.setText(q.optionC)
            dialogView.etOptionD.setText(q.optionD)
            when (q.correctAnswer) {
                "B" -> dialogView.rbB.isChecked = true
                "C" -> dialogView.rbC.isChecked = true
                "D" -> dialogView.rbD.isChecked = true
                else -> dialogView.rbA.isChecked = true
            }
            // Difficulty removed
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView.root)
            .create()

        // Makes the custom glass background transparent
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogView.btnSave.setOnClickListener {
            val question = dialogView.etQuestion.text.toString().trim()
            val optionA = dialogView.etOptionA.text.toString().trim()
            val optionB = dialogView.etOptionB.text.toString().trim()
            val optionC = dialogView.etOptionC.text.toString().trim()
            val optionD = dialogView.etOptionD.text.toString().trim()
            val correctAnswer = when (dialogView.radioGroupCorrect.checkedRadioButtonId) {
                dialogView.rbB.id -> "B"
                dialogView.rbC.id -> "C"
                dialogView.rbD.id -> "D"
                else -> "A"
            }

            if (question.isEmpty() || optionA.isEmpty() || optionB.isEmpty() ||
                optionC.isEmpty() || optionD.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val questionId = editingQuestionId ?: "q${System.currentTimeMillis()}"

            val questionObj = Question(
                id = questionId,
                question = question,
                optionA = optionA,
                optionB = optionB,
                optionC = optionC,
                optionD = optionD,
                correctAnswer = correctAnswer,
                difficulty = "" // Passed empty since we removed it from UI
            )

            dialogView.btnSave.isEnabled = false
            dialogView.btnSave.text = "Saving..."

            FirebaseDatabase.getInstance().getReference("questions")
                .child(selectedTopicId)
                .child(questionId)
                .setValue(questionObj)
                .addOnSuccessListener {
                    updateQuestionCount()
                    Toast.makeText(requireContext(), "Question saved!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to save!", Toast.LENGTH_SHORT).show()
                    dialogView.btnSave.isEnabled = true
                    dialogView.btnSave.text = "Save"
                }
        }

        dialog.show()
    }

    private fun showBulkAddDialog() {
        // Connect to your brand new Bulk Add XML layout
        val dialogView = DialogBulkAddBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView.root)
            .create()

        // Apply glassmorphism transparency
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogView.btnImport.setOnClickListener {
            val json = dialogView.etJsonInput.text.toString().trim()
            if (json.isEmpty()) {
                Toast.makeText(requireContext(), "Please paste JSON data first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parseBulkQuestions(json)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun parseBulkQuestions(json: String) {
        try {
            val jsonArray = JSONArray(json)
            var savedCount = 0

            for (i in 0 until jsonArray.length()) {
                val obj: JSONObject = jsonArray.getJSONObject(i)
                val questionId = "q${System.currentTimeMillis()}_$i"
                val question = Question(
                    id = questionId,
                    question = obj.getString("question"),
                    optionA = obj.getString("optionA"),
                    optionB = obj.getString("optionB"),
                    optionC = obj.getString("optionC"),
                    optionD = obj.getString("optionD"),
                    correctAnswer = obj.getString("correctAnswer"),
                    difficulty = "" // Removed difficulty requirement
                )

                FirebaseDatabase.getInstance().getReference("questions")
                    .child(selectedTopicId)
                    .child(questionId)
                    .setValue(question)
                    .addOnSuccessListener {
                        savedCount++
                        if (savedCount == jsonArray.length()) {
                            updateQuestionCount()
                            Toast.makeText(
                                requireContext(),
                                "$savedCount questions imported!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Invalid JSON format! Check and try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updateQuestionCount() {
        FirebaseDatabase.getInstance().getReference("questions")
            .child(selectedTopicId)
            .get()
            .addOnSuccessListener { snapshot ->
                val count = snapshot.childrenCount.toInt()
                FirebaseDatabase.getInstance().getReference("topics")
                    .child(selectedTopicId)
                    .child("questionCount")
                    .setValue(count)
            }
    }

    private fun confirmDelete(question: Question) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Question")
            .setMessage("Are you sure you want to delete this question?")
            .setPositiveButton("Delete") { _, _ ->
                FirebaseDatabase.getInstance().getReference("questions")
                    .child(selectedTopicId)
                    .child(question.id)
                    .removeValue()
                    .addOnSuccessListener {
                        updateQuestionCount()
                        Toast.makeText(requireContext(), "Question deleted!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to delete!", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}