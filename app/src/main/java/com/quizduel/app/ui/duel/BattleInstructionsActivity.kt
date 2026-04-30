package com.quizduel.app.ui.duel

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.quizduel.app.ui.home.DialogUtils
import com.quizduel.app.R
import com.quizduel.app.databinding.ActivityBattleInstructionsBinding
import com.quizduel.app.ui.home.HomeActivity
import com.quizduel.app.utils.NetworkUtils

class BattleInstructionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBattleInstructionsBinding
    private lateinit var roomCode: String
    private lateinit var playerSlot: String
    private lateinit var opponentSlot: String
    private var countdownTimer: CountDownTimer? = null

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    private val heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var heartbeatRunnable: Runnable

    private val db = FirebaseDatabase.getInstance().reference
    private var opponentLeftListener: ValueEventListener? = null
    private var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBattleInstructionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!NetworkUtils.isInternetAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        window.statusBarColor = getColor(R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        roomCode = intent.getStringExtra("ROOM_CODE") ?: run { finish(); return }
        playerSlot = intent.getStringExtra("PLAYER_SLOT") ?: "player1"
        opponentSlot = if (playerSlot == "player1") "player2" else "player1"

        val playerRef = db.child("rooms").child(roomCode).child("players").child(playerSlot)

// 🔥 instant disconnect handling (NO DELAY)
        playerRef.onDisconnect().removeValue()

        db.child("rooms").child(roomCode).child("status")
            .onDisconnect().setValue("abandoned")

        val lastSeenRef = db.child("rooms").child(roomCode)
            .child("players").child(playerSlot).child("lastSeen")

        heartbeatRunnable = object : Runnable {
            override fun run() {
                lastSeenRef.setValue(ServerValue.TIMESTAMP)
                heartbeatHandler.postDelayed(this, 3000)
            }
        }

        heartbeatHandler.post(heartbeatRunnable)

        listenForOpponentLeaving()

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                runOnUiThread {
                    if (!isFinishing) {
                        db.child("rooms").child(roomCode).child("status").setValue("abandoned")
                        Toast.makeText(this@BattleInstructionsActivity, "Internet lost", Toast.LENGTH_SHORT).show()
                        goHome()
                    }
                }
            }
        }

        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        db.child("rooms").child(roomCode).child("instructionStart")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val start = snapshot.getValue(Boolean::class.java) ?: false
                    if (start && countdownTimer == null) {
                        startAutoCountdown()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        if (playerSlot == "player1") {
            db.child("rooms").child(roomCode).child("instructionStart").setValue(true)
        }
    }

    private fun listenForOpponentLeaving() {
        opponentLeftListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isNavigating || isFinishing) return

                val status = snapshot.child("status").getValue(String::class.java)
                val opponentExists = snapshot.child("players").hasChild(opponentSlot)

                val lastSeen = snapshot.child("players")
                    .child(opponentSlot)
                    .child("lastSeen")
                    .getValue(Long::class.java)

                val currentTime = System.currentTimeMillis()

                // ✅ PRIORITY 1: opponent left intentionally or room abandoned
                if ((status == "abandoned" || !opponentExists) && !isNavigating) {
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

// ✅ PRIORITY 2: silent disconnect (internet off)
                else if (lastSeen != null && currentTime - lastSeen > 8000 && !isNavigating) {
                    isNavigating = true
                    countdownTimer?.cancel()

                    DialogUtils.showAlertDialog(
                        context = this@BattleInstructionsActivity,
                        title = "Opponent Disconnected",
                        message = "Connection lost with opponent.",
                        iconEmoji = "⚠️",
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

                if (!NetworkUtils.isInternetAvailable(this@BattleInstructionsActivity)) {
                    Toast.makeText(this@BattleInstructionsActivity, "No internet connection", Toast.LENGTH_SHORT).show()
                    return
                }
                db.child("rooms").child(roomCode)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {

                            val roomStatus = snapshot.child("status").getValue(String::class.java)

                            if (roomStatus == "abandoned") {
                                Toast.makeText(
                                    this@BattleInstructionsActivity,
                                    "Opponent left",
                                    Toast.LENGTH_SHORT
                                ).show()
                                goHome()
                                return
                            }

                            isNavigating = true
                            binding.tvCountdown.text = "FIGHT! ⚔️"

                            startActivity(
                                Intent(
                                    this@BattleInstructionsActivity,
                                    BattleActivity::class.java
                                ).apply {
                                    putExtra("ROOM_CODE", roomCode)
                                    putExtra("PLAYER_SLOT", playerSlot)
                                }
                            )
                            finish()
                        }
                        override fun onCancelled(error: DatabaseError) {
                            // you can leave it empty
                        }
                    })
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
        if (::connectivityManager.isInitialized) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
    }
}