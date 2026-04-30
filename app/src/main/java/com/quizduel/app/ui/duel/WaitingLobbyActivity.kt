package com.quizduel.app.ui.duel

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.quizduel.app.databinding.ActivityWaitingLobbyBinding
import coil.load
import coil.decode.SvgDecoder
import com.quizduel.app.R
import com.quizduel.app.ui.profile.AvatarUtils
import com.quizduel.app.utils.NetworkUtils
import android.net.Network
import android.content.Context

class WaitingLobbyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWaitingLobbyBinding
    private val db = FirebaseDatabase.getInstance().reference

    private lateinit var roomCode: String
    private lateinit var playerSlot: String
    private var isQuickMatch = false

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    private val heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var heartbeatRunnable: Runnable

    private var roomListener: ValueEventListener? = null
    private var countdownTimer: CountDownTimer? = null
    private var navigated = false
    private var countdownStarted = false

    companion object {
        const val QUICK_MATCH_TIMEOUT_MS = 60_000L
        const val EXTRA_IS_QUICK_MATCH = "IS_QUICK_MATCH"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWaitingLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!NetworkUtils.isInternetAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        roomCode = intent.getStringExtra("ROOM_CODE") ?: run { finish(); return }
        playerSlot = intent.getStringExtra("PLAYER_SLOT") ?: "player1"
        isQuickMatch = intent.getBooleanExtra(EXTRA_IS_QUICK_MATCH, false)

        val playerRef = db.child("rooms").child(roomCode).child("players").child(playerSlot)

// 🔥 instant disconnect handling (fix delay issue)
        playerRef.onDisconnect().removeValue()

        db.child("rooms").child(roomCode).child("status")
            .onDisconnect().setValue("abandoned")

        val lastSeenRef = db.child("rooms").child(roomCode)
            .child("players").child(playerSlot).child("lastSeen")

        heartbeatRunnable = object : Runnable {
            override fun run() {
                lastSeenRef.setValue(ServerValue.TIMESTAMP)
                heartbeatHandler.postDelayed(this, 3000) // every 3 sec
            }
        }

        heartbeatHandler.post(heartbeatRunnable)

        binding.tvRoomCode.text = roomCode
        binding.btnCopyCode.setOnClickListener { copyCodeToClipboard() }
        binding.btnCancel.setOnClickListener { cancelAndLeave() }

        // Both players listen for the countdownStart flag written by player1
        listenForOpponent()

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                runOnUiThread {
                    if (!isFinishing) {

                        db.child("rooms").child(roomCode).child("status").setValue("abandoned")

                        Toast.makeText(this@WaitingLobbyActivity, "Internet lost", Toast.LENGTH_SHORT).show()
                        cancelAndLeave()
                    }
                }
            }
        }

        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        db.child("rooms").child(roomCode).child("countdownStart")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val started = snapshot.getValue(Boolean::class.java) ?: false

                    if (started && !countdownStarted) {
                        countdownStarted = true

                        countdownTimer?.cancel()
                        binding.tvTimeout.visibility = View.GONE
                        binding.btnCancel.visibility = View.GONE
                        binding.btnCopyCode.visibility = View.GONE

                        start321Countdown() // 🔥 BOTH players start from here
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })

        if (isQuickMatch) startTimeoutCountdown()
    }

    private fun listenForOpponent() {
        roomListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                val status = snapshot.child("status").getValue(String::class.java)

                val lastSeen = snapshot.child("players")
                    .child(if (playerSlot == "player1") "player2" else "player1")
                    .child("lastSeen")
                    .getValue(Long::class.java)

                val currentTime = System.currentTimeMillis()

                if (status == "abandoned" && !navigated) {
                    navigated = true
                    Toast.makeText(this@WaitingLobbyActivity, "Opponent left", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }

                if (lastSeen != null && currentTime - lastSeen > 8000 && !navigated) {
                    navigated = true
                    Toast.makeText(this@WaitingLobbyActivity, "Opponent disconnected", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }

                if (navigated) return

                val topicName = snapshot.child("topicName").getValue(String::class.java) ?: ""
                val questionCount = snapshot.child("questionCount").getValue(Int::class.java) ?: 0
                binding.tvTopicName.text = topicName
                binding.tvQuestionCount.text = questionCount.toString()

                val p1Uid = snapshot.child("players").child("player1").child("uid").getValue(String::class.java)
                val p1Name = snapshot.child("players").child("player1").child("username").getValue(String::class.java) ?: ""

                binding.tvPlayer1Name.text = if (playerSlot == "player1") "You ($p1Name)" else p1Name

                // Fetch and load Player 1 Avatar
                if (!p1Uid.isNullOrEmpty()) {
                    db.child("users").child(p1Uid).child("avatarId").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot) {
                            val avatarId = snap.getValue(Int::class.java) ?: 1
                            binding.ivPlayer1Avatar.load(AvatarUtils.getAvatarUrl(avatarId)) {
                                decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }

                val p2Uid = snapshot.child("players").child("player2").child("uid").getValue(String::class.java)
                val p2Name = snapshot.child("players").child("player2").child("username").getValue(String::class.java) ?: ""

                if (!p2Uid.isNullOrEmpty()) {
                    binding.tvPlayer2Name.text = if (playerSlot == "player2") "You ($p2Name)" else p2Name
                    binding.tvPlayer2Status.text = "Ready ✓"
                    binding.tvPlayer2Status.setTextColor(androidx.core.content.ContextCompat.getColor(this@WaitingLobbyActivity, R.color.difficulty_easy))

                    binding.ivPlayer2Avatar.imageTintList = null
                    binding.ivPlayer2Avatar.setPadding(0, 0, 0, 0)

                    db.child("users").child(p2Uid).child("avatarId").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot) {
                            val avatarId = snap.getValue(Int::class.java) ?: 1
                            binding.ivPlayer2Avatar.load(AvatarUtils.getAvatarUrl(avatarId)) {
                                decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                } else {
                    // THE FIX: PLAYER 2 LEFT THE LOBBY!
                    // Reset the UI back to waiting mode
                    binding.tvPlayer2Name.text = "Waiting..."
                    binding.tvPlayer2Status.text = "Not joined"
                    binding.tvPlayer2Status.setTextColor(androidx.core.content.ContextCompat.getColor(this@WaitingLobbyActivity, R.color.clay_text_muted))
                    binding.ivPlayer2Avatar.setImageResource(R.drawable.ic_friends)
                    binding.ivPlayer2Avatar.imageTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this@WaitingLobbyActivity, R.color.clay_text_muted))
                    binding.ivPlayer2Avatar.setPadding(32, 32, 32, 32) // Add padding back for the icon

                    // Cancel the 3-second timer if it started
                    if (playerSlot == "player1" && countdownStarted) {
                        countdownStarted = false
                        countdownTimer?.cancel()
                        db.child("rooms").child(roomCode).child("countdownStart").setValue(false)
                        binding.tvStatus.text = "Waiting for opponent to join…"
                    }
                }

                if (playerSlot == "player1" && !p2Uid.isNullOrEmpty() && !countdownStarted) {
                    countdownTimer?.cancel()
                    binding.tvTimeout.visibility = View.GONE
                    binding.btnCancel.visibility = View.GONE
                    binding.btnCopyCode.visibility = View.GONE
                    db.child("rooms").child(roomCode).child("countdownStart").setValue(true)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@WaitingLobbyActivity, "Connection error", Toast.LENGTH_SHORT).show()
            }
        }
        db.child("rooms").child(roomCode).addValueEventListener(roomListener!!)
    }

    private fun start321Countdown() {
        var secondsLeft = 3
        binding.tvStatus.text = "Battle starts in $secondsLeft…"

        countdownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                binding.tvStatus.text = "Battle starts in $secondsLeft…"
            }

            override fun onFinish() {
                binding.tvStatus.text = "GO! ⚔️"
                if (!navigated) {
                    navigated = true
                    startActivity(
                        Intent(this@WaitingLobbyActivity, BattleInstructionsActivity::class.java).apply {
                            putExtra("ROOM_CODE", roomCode)
                            putExtra("PLAYER_SLOT", playerSlot)
                        }
                    )
                    finish()
                }
            }
        }.start()
    }

    private fun startTimeoutCountdown() {
        binding.tvTimeout.visibility = View.VISIBLE
        countdownTimer = object : CountDownTimer(QUICK_MATCH_TIMEOUT_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvTimeout.text = "Searching… ${millisUntilFinished / 1000}s remaining"
            }

            override fun onFinish() {
                if (!navigated) {
                    db.child("rooms").child(roomCode).removeValue()
                    db.child("quickmatch").child("waiting").removeValue()
                    Toast.makeText(
                        this@WaitingLobbyActivity,
                        "No opponent found. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }.start()
    }

    private fun copyCodeToClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Room Code", roomCode)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Room code copied!", Toast.LENGTH_SHORT).show()
    }

    private fun cancelAndLeave() {
        countdownTimer?.cancel()
        if (playerSlot == "player1") {
            db.child("rooms").child(roomCode).removeValue()
            if (isQuickMatch) db.child("quickmatch").child("waiting").removeValue()
        } else {
            db.child("rooms").child(roomCode).child("players").child("player2").removeValue()
            db.child("rooms").child(roomCode).child("status").setValue("waiting")
        }
        finish()
    }

    override fun onBackPressed() {
        cancelAndLeave()
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        roomListener?.let {
            db.child("rooms").child(roomCode).removeEventListener(it)
        }
        if (::connectivityManager.isInitialized) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
    }
}