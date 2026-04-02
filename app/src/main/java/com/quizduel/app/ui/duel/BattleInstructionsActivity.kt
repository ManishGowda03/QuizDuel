package com.quizduel.app.ui.duel

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.quizduel.app.ui.home.DialogUtils
import com.quizduel.app.R
import com.quizduel.app.databinding.ActivityBattleInstructionsBinding
import com.quizduel.app.ui.home.HomeActivity

class BattleInstructionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBattleInstructionsBinding
    private lateinit var roomCode: String
    private lateinit var playerSlot: String
    private lateinit var opponentSlot: String
    private var countdownTimer: CountDownTimer? = null

    private val db = FirebaseDatabase.getInstance().reference
    private var opponentLeftListener: ValueEventListener? = null
    private var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBattleInstructionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        roomCode = intent.getStringExtra("ROOM_CODE") ?: run { finish(); return }
        playerSlot = intent.getStringExtra("PLAYER_SLOT") ?: "player1"
        opponentSlot = if (playerSlot == "player1") "player2" else "player1"

        listenForOpponentLeaving()
        startAutoCountdown()
    }

    private fun listenForOpponentLeaving() {
        opponentLeftListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isNavigating || isFinishing) return

                val status = snapshot.child("status").getValue(String::class.java)
                val opponentExists = snapshot.child("players").hasChild(opponentSlot)

                // If the opponent disconnects or the room is flagged as abandoned
                if (status == "abandoned" || !opponentExists) {
                    isNavigating = true
                    countdownTimer?.cancel()

                    DialogUtils.showAlertDialog(
                        context = this@BattleInstructionsActivity,
                        title = "Opponent Left",
                        message = "Your opponent got cold feet and ran away!",
                        iconEmoji = "🏃‍♂️",
                        onConfirm = {
                            goHome()
                        }
                    )
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("rooms").child(roomCode).addValueEventListener(opponentLeftListener!!)
    }

    private fun startAutoCountdown() {
        countdownTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                binding.tvCountdown.text = "Battle starts in ${secondsLeft}s..."
            }

            override fun onFinish() {
                if (isNavigating || isFinishing) return
                isNavigating = true
                binding.tvCountdown.text = "FIGHT! ⚔️"

                startActivity(
                    Intent(this@BattleInstructionsActivity, BattleActivity::class.java).apply {
                        putExtra("ROOM_CODE", roomCode)
                        putExtra("PLAYER_SLOT", playerSlot)
                    }
                )
                finish()
            }
        }.start()
    }

    // If THIS player hits the back button during instructions, kill the room
    override fun onBackPressed() {
        isNavigating = true
        countdownTimer?.cancel()
        db.child("rooms").child(roomCode).child("status").setValue("abandoned")
        goHome()
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        opponentLeftListener?.let {
            db.child("rooms").child(roomCode).removeEventListener(it)
        }
    }
}