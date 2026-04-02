package com.quizduel.app.ui.admin

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.quizduel.app.data.model.Topic
import com.quizduel.app.databinding.DialogAddTopicBinding
import com.quizduel.app.databinding.FragmentAdminTopicsBinding
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

class AdminTopicsFragment : Fragment() {

    private var _binding: FragmentAdminTopicsBinding? = null
    private val binding get() = _binding!!

    private lateinit var topicAdapter: AdminTopicAdapter

    // TWO LISTS: One holds everything, one holds the search results
    private val allTopics = mutableListOf<Topic>()
    private val displayedTopics = mutableListOf<Topic>()

    private var selectedImageUri: Uri? = null
    private var editingTopicId: String? = null
    private var dialogBinding: DialogAddTopicBinding? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let { uri ->
                dialogBinding?.let { db ->
                    db.ivTopicImage.visibility = View.VISIBLE
                    Glide.with(this).load(uri).into(db.ivTopicImage)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminTopicsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapter()
        setupSearch()
        loadTopics()

        binding.fabAddTopic.setOnClickListener {
            showAddTopicDialog(null)
        }
    }

    private fun setupAdapter() {
        // Adapter uses the displayed (filtered) list
        topicAdapter = AdminTopicAdapter(
            displayedTopics,
            onEdit = { topic -> showAddTopicDialog(topic) },
            onDelete = { topic -> confirmDelete(topic) }
        )
        binding.rvTopics.adapter = topicAdapter
    }

    private fun setupSearch() {
        binding.etSearchTopic.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterTopics(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterTopics(query: String) {
        displayedTopics.clear()
        if (query.isEmpty()) {
            displayedTopics.addAll(allTopics)
        } else {
            val lowerCaseQuery = query.lowercase()
            displayedTopics.addAll(allTopics.filter {
                it.name.lowercase().contains(lowerCaseQuery) ||
                        it.category.lowercase().contains(lowerCaseQuery)
            })
        }
        topicAdapter.notifyDataSetChanged()

        binding.tvTopicCount.text = "${displayedTopics.size} Topics"
        binding.layoutEmpty.visibility = if (displayedTopics.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadTopics() {
        FirebaseDatabase.getInstance().getReference("topics")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    allTopics.clear()
                    for (topicSnap in snapshot.children) {
                        val topic = topicSnap.getValue(Topic::class.java)
                        if (topic != null) allTopics.add(topic)
                    }

                    // Re-apply the search filter instantly when data changes
                    filterTopics(binding.etSearchTopic.text.toString())
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun showAddTopicDialog(existingTopic: Topic?) {
        val dialogView = DialogAddTopicBinding.inflate(layoutInflater)
        dialogBinding = dialogView
        selectedImageUri = null
        editingTopicId = existingTopic?.id

        // Pre-fill if editing
        existingTopic?.let { topic ->
            dialogView.tvDialogTitle.text = "Edit Topic"
            dialogView.etTopicName.setText(topic.name)
            dialogView.etCategory.setText(topic.category)
            when (topic.difficulty) {
                "Medium" -> dialogView.rbMedium.isChecked = true
                "Hard" -> dialogView.rbHard.isChecked = true
                else -> dialogView.rbEasy.isChecked = true
            }

            if (topic.imageUrl.isNotEmpty()) {
                dialogView.ivTopicImage.visibility = View.VISIBLE
                try {
                    val imageBytes = Base64.decode(topic.imageUrl, Base64.DEFAULT)
                    Glide.with(this).load(imageBytes).into(dialogView.ivTopicImage)
                } catch (e: Exception) {
                    Glide.with(this).load(topic.imageUrl).into(dialogView.ivTopicImage)
                }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView.root)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogView.cardImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            imagePickerLauncher.launch(intent)
        }

        dialogView.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogView.btnSave.setOnClickListener {
            val name = dialogView.etTopicName.text.toString().trim()
            val category = dialogView.etCategory.text.toString().trim()
            val difficulty = when (dialogView.radioGroupDifficulty.checkedRadioButtonId) {
                dialogView.rbMedium.id -> "Medium"
                dialogView.rbHard.id -> "Hard"
                else -> "Easy"
            }

            if (name.isEmpty()) {
                dialogView.tilTopicName.error = "Topic name is required"
                return@setOnClickListener
            }

            dialogView.btnSave.isEnabled = false
            dialogView.btnSave.text = "Saving..."

            val topicId = editingTopicId ?: name.lowercase().replace(" ", "_")
            val existingCount = existingTopic?.questionCount ?: 0

            if (selectedImageUri != null) {
                uploadImageAndSaveTopic(
                    topicId, name, category, "",
                    difficulty, selectedImageUri!!, dialog,
                    existingTopic?.imageUrl ?: "", existingCount
                )
            } else {
                saveTopic(
                    topicId, name, category, "",
                    difficulty, existingTopic?.imageUrl ?: "", dialog, existingCount
                )
            }
        }

        dialog.show()
    }

    private fun uploadImageAndSaveTopic(
        topicId: String, name: String, category: String,
        icon: String, difficulty: String, imageUri: Uri,
        dialog: AlertDialog, existingImageUrl: String, existingCount: Int
    ) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 300, 300, true)

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val imageBytes = outputStream.toByteArray()

            val base64ImageString = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            saveTopic(topicId, name, category, icon, difficulty, base64ImageString, dialog, existingCount)

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to process image!", Toast.LENGTH_SHORT).show()
            dialogBinding?.btnSave?.isEnabled = true
            dialogBinding?.btnSave?.text = "Save"
        }
    }

    private fun saveTopic(
        topicId: String, name: String, category: String,
        icon: String, difficulty: String, imageUrl: String,
        dialog: AlertDialog, existingCount: Int
    ) {
        val topic = Topic(
            id = topicId,
            name = name,
            category = category,
            icon = icon,
            difficulty = difficulty,
            imageUrl = imageUrl,
            isActive = true,
            questionCount = existingCount
        )

        FirebaseDatabase.getInstance().getReference("topics")
            .child(topicId)
            .setValue(topic)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Topic saved!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to save topic!", Toast.LENGTH_SHORT).show()
                dialogBinding?.btnSave?.isEnabled = true
                dialogBinding?.btnSave?.text = "Save"
            }
    }

    private fun confirmDelete(topic: Topic) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Topic")
            .setMessage("Are you sure you want to delete '${topic.name}'? This will NOT delete its questions.")
            .setPositiveButton("Delete") { _, _ ->
                FirebaseDatabase.getInstance().getReference("topics")
                    .child(topic.id)
                    .removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Topic deleted!", Toast.LENGTH_SHORT).show()
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
        dialogBinding = null
    }
}