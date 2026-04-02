package com.quizduel.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.quizduel.app.R
import com.quizduel.app.databinding.ActivityLoginBinding
import com.quizduel.app.ui.home.HomeActivity
import com.quizduel.app.ui.admin.AdminActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Match the status bar color to the clay background
        window.statusBarColor = getColor(R.color.clay_bg)

        // 2. Ensure the time and battery icons stay dark so they are readable!
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true

        auth = FirebaseAuth.getInstance()

        // If already logged in, skip to correct screen
        if (auth.currentUser != null) {
            // Force refresh token to avoid stale cached data
            auth.currentUser!!.getIdToken(true)
                .addOnSuccessListener {
                    fetchRoleAndNavigate(auth.currentUser!!.uid)
                }
                .addOnFailureListener {
                    // Token refresh failed — force logout and show login screen
                    auth.signOut()
                }
            return
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (!validateInputs(email, password)) return@setOnClickListener

            showLoading(true)

            // FIX: Sign out first to clear any cached session
            // before signing in with new credentials
            auth.signOut()

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                        fetchRoleAndNavigate(uid)
                    } else {
                        showLoading(false)
                        Toast.makeText(
                            this,
                            task.exception?.message ?: "Login failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                binding.tilEmail.error = "Enter your email first"
                return@setOnClickListener
            }
            if (!isValidGmail(email)) {
                binding.tilEmail.error = "Enter a valid @gmail.com address"
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Reset email sent! Check your inbox and spam folder.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this, task.exception?.message, Toast.LENGTH_SHORT).show()
                    }
                }
        }

        binding.btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
        }
    }

    private fun fetchRoleAndNavigate(uid: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        userRef.get().addOnSuccessListener { snapshot ->
            showLoading(false)
            if (!snapshot.exists()) {
                val email = auth.currentUser?.email ?: ""
                val username = email.substringBefore("@")
                val userMap = mapOf(
                    "uid" to uid,
                    "username" to username,
                    "email" to email,
                    "role" to "player",
                    "totalScore" to 0,
                    "matchesPlayed" to 0,
                    "wins" to 0
                )
                userRef.setValue(userMap).addOnSuccessListener {
                    navigateToHome()
                }
            } else {
                val role = snapshot.child("role").getValue(String::class.java) ?: "player"
                if (role == "admin") {
                    navigateToAdmin()
                } else {
                    navigateToHome()
                }
            }
        }.addOnFailureListener {
            showLoading(false)
            Toast.makeText(this, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        // FIX: Clear entire task stack so no old fragments are cached
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    private fun navigateToAdmin() {
        val intent = Intent(this, AdminActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"
            isValid = false
        } else if (!isValidGmail(email)) {
            binding.tilEmail.error = "Must be a valid @gmail.com address"
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            isValid = false
        } else if (!isValidPassword(password)) {
            binding.tilPassword.error = "Min 8 chars, upper, lower, number & symbol"
            isValid = false
        } else {
            binding.tilPassword.error = null
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
        binding.btnLogin.isEnabled = !show
    }
}