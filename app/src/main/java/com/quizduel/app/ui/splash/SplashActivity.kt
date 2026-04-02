package com.quizduel.app.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth
import com.quizduel.app.databinding.ActivitySplashBinding
import com.quizduel.app.ui.auth.LoginActivity
import com.quizduel.app.ui.home.HomeActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide the status bar and navigation bar for a full-screen Supercell vibe
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        playIntroAnimation()
    }

    private fun playIntroAnimation() {

        // Phase 1 — QUIZDUEL bursts in from center
        binding.tvAppName.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(1400)
            .setStartDelay(200)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()

        // Phase 2 — tagline scales up from center (like QuizDuel)
        binding.tvTagline.animate()
            .scaleX(1f).scaleY(1f)
            .alpha(1f)
            .setDuration(1200)
            .setStartDelay(1900)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .withEndAction { navigateToNextScreen() }
            .start()
    }

    private fun navigateToNextScreen() {
        val destination = if (auth.currentUser != null) {
            Intent(this, HomeActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        startActivity(destination)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}