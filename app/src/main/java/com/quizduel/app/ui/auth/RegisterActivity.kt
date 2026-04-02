package com.quizduel.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.quizduel.app.R
import com.quizduel.app.databinding.ActivityRegisterBinding
import com.quizduel.app.ui.home.HomeActivity
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Match the status bar color to the clay background
        window.statusBarColor = getColor(R.color.clay_bg)

        // 2. Ensure the time and battery icons stay dark so they are readable!
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true

        auth = FirebaseAuth.getInstance()

        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()
            val role = "player"

            if (!validateInputs(username, email, password, confirmPassword)) return@setOnClickListener

            showLoading(true)

            // We moved the logic out of here into a specific checking function
            checkUsernameUniqueAndRegister(username, email, password, role)
        }

        binding.tvBackToLogin.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }

    private fun checkUsernameUniqueAndRegister(username: String, email: String, password: String, role: String) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users")

        // Query the database to see if any child node has this specific username
        usersRef.orderByChild("username").equalTo(username)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // The username is already taken!
                        showLoading(false)
                        binding.tilUsername.error = "This username is already taken in the arena!"
                        Toast.makeText(this@RegisterActivity, "Username taken. Try another one.", Toast.LENGTH_SHORT).show()
                    } else {
                        // Username is free, clear any previous errors and proceed to Auth
                        binding.tilUsername.error = null
                        registerWithFirebaseAuth(username, email, password, role)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showLoading(false)
                    Toast.makeText(this@RegisterActivity, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun registerWithFirebaseAuth(username: String, email: String, password: String, role: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                    saveUserToDatabase(uid, username, email, role)
                } else {
                    showLoading(false)

                    // Explicitly check if the email is already registered
                    if (task.exception is FirebaseAuthUserCollisionException) {
                        binding.tilEmail.error = "An account with this email already exists!"
                        Toast.makeText(this, "Email already in use.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this,
                            task.exception?.message ?: "Registration failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
    }

    private fun saveUserToDatabase(uid: String, username: String, email: String, role: String) {
        val userMap = mapOf(
            "uid" to uid,
            "username" to username,
            "email" to email,
            "role" to role,
            "totalScore" to 0,
            "matchesPlayed" to 0,
            "wins" to 0
        )

        FirebaseDatabase.getInstance().getReference("users")
            .child(uid)
            .setValue(userMap)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Welcome to the arena, $username! 🏆", Toast.LENGTH_SHORT).show()
                navigateBasedOnRole(role)
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Failed to save user data. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateBasedOnRole(role: String) {
        val intent = if (role == "admin") {
            // AdminActivity coming in Step 6 — go Home for now
            Intent(this, HomeActivity::class.java)
        } else {
            Intent(this, HomeActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun validateInputs(
        username: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true

        // Username validation
        if (username.isEmpty()) {
            binding.tilUsername.error = "Username is required"
            isValid = false
        } else if (username.length < 3) {
            binding.tilUsername.error = "Minimum 3 characters"
            isValid = false
        } else if (username.contains(" ")) {
            binding.tilUsername.error = "No spaces allowed"
            isValid = false
        } else {
            binding.tilUsername.error = null
        }

        // Email validation
        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"
            isValid = false
        } else if (!isValidGmail(email)) {
            binding.tilEmail.error = "Must be a valid @gmail.com address"
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        // Password validation
        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            isValid = false
        } else if (!isValidPassword(password)) {
            binding.tilPassword.error = "Min 8 chars, upper, lower, number & symbol required"
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        // Confirm password validation
        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = "Please confirm your password"
            isValid = false
        } else if (confirmPassword != password) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            isValid = false
        } else {
            binding.tilConfirmPassword.error = null
        }

        return isValid
    }

    private fun isValidGmail(email: String): Boolean {
        return email.endsWith("@gmail.com") && email.length > 10
    }

    private fun isValidPassword(password: String): Boolean {
        if (password.length < 8) return false
        if (!password.any { it.isUpperCase() }) return false
        if (!password.any { it.isLowerCase() }) return false
        if (!password.any { it.isDigit() }) return false
        if (!password.any { !it.isLetterOrDigit() }) return false
        return true
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !show
    }
}