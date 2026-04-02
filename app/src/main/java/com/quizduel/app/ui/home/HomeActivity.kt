package com.quizduel.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.activity.OnBackPressedCallback
import coil.load
import coil.decode.SvgDecoder
import coil.transform.CircleCropTransformation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.quizduel.app.R
import com.quizduel.app.data.model.BattleInvite
import com.quizduel.app.data.model.RoomPlayer
import com.quizduel.app.databinding.ActivityHomeBinding
import com.quizduel.app.ui.duel.BattleFragment
import com.quizduel.app.ui.duel.WaitingLobbyActivity
import com.quizduel.app.ui.leaderboard.LeaderboardFragment
import com.quizduel.app.ui.profile.AvatarUtils
import com.quizduel.app.ui.profile.ProfileFragment
import com.quizduel.app.ui.admin.AdminActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private var inviteListener: ValueEventListener? = null
    private var isInviteDialogShowing = false
    private var navigatedToLobby = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If current user is admin, redirect to AdminActivity
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseDatabase.getInstance().getReference("users")
                .child(uid).child("role")
                .get()
                .addOnSuccessListener { snapshot ->
                    val role = snapshot.getValue(String::class.java) ?: "player"
                    if (role == "admin") {
                        startActivity(Intent(this, AdminActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    }
                }
        }

        supportFragmentManager.popBackStack(null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)

        setOnlineStatus(true)

        // KEYBOARD FIX: Hide Bottom Nav when typing in the search bar
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            binding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = binding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            if (keypadHeight > screenHeight * 0.15) { // Keyboard is open
                binding.bottomNavigation.visibility = android.view.View.GONE
            } else { // Keyboard is closed
                binding.bottomNavigation.visibility = android.view.View.VISIBLE
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.bottomNavigation.selectedItemId != R.id.nav_home) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_home
                }
            }
        })

        loadFragment(HomeFragment())

        binding.bottomNavigation.setOnItemSelectedListener { item ->

            // 1. Reset all icons back down to flat
            for (i in 0 until binding.bottomNavigation.menu.size()) {
                val menuId = binding.bottomNavigation.menu.getItem(i).itemId
                val menuView = binding.bottomNavigation.findViewById<android.view.View>(menuId)
                menuView?.animate()?.translationY(0f)?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(150)?.start()
            }

            // 2. Float the selected icon up slightly (adjusted to -16f so it's not too high)
            val itemView = binding.bottomNavigation.findViewById<android.view.View>(item.itemId)
            itemView?.let { view ->
                view.animate().translationY(-16f).scaleX(1.15f).scaleY(1.15f).setDuration(150).start()
            }

            // Fragment Navigation
            when (item.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_battle -> loadFragment(BattleFragment())
                R.id.nav_leaderboard -> loadFragment(LeaderboardFragment())
                R.id.nav_profile -> loadFragment(ProfileFragment())
            }
            true
        }
    }

    override fun onStart() {
        super.onStart()
        val uid = auth.currentUser?.uid ?: return
        inviteListener?.let {
            db.child("battleInvites").child(uid).removeEventListener(it)
        }
        navigatedToLobby = false
        isInviteDialogShowing = false
        listenForBattleInvites()
    }

    override fun onResume() {
        super.onResume()
        setOnlineStatus(true)
    }

    private fun setOnlineStatus(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.child("users").child(uid)
        if (isOnline) {
            userRef.child("isOnline").setValue(true)
            userRef.child("isOnline").onDisconnect().setValue(false)
            userRef.child("lastSeen").onDisconnect().setValue(System.currentTimeMillis())
        }
    }

    // ── KEY FIX: flag is NOT set before calling showBattleInviteDialog ────────
    private fun listenForBattleInvites() {
        val uid = auth.currentUser?.uid ?: return

        inviteListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { child ->
                    val status = child.child("status")
                        .getValue(String::class.java) ?: return@forEach
                    val inviteId = child.key ?: return@forEach
                    val invite = child.getValue(BattleInvite::class.java) ?: return@forEach

                    when (status) {
                        "pending" -> {
                            if (!isInviteDialogShowing && !isFinishing) {
                                // DO NOT set isInviteDialogShowing here
                                // showBattleInviteDialog handles the flag itself
                                showBattleInviteDialog(invite)
                            }
                        }
                        "rejected" -> {
                            if (invite.fromUid == uid) {
                                db.child("battleInvites").child(uid)
                                    .child(inviteId).removeValue()
                                Toast.makeText(
                                    this@HomeActivity,
                                    "${invite.fromUsername} rejected your battle invite",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        "accepted" -> {
                            if (invite.fromUid == uid && !navigatedToLobby) {
                                navigatedToLobby = true
                                db.child("battleInvites").child(uid)
                                    .child(inviteId).removeValue()
                                startActivity(
                                    Intent(this@HomeActivity, WaitingLobbyActivity::class.java).apply {
                                        putExtra("ROOM_CODE", invite.roomCode)
                                        putExtra("PLAYER_SLOT", "player1")
                                    }
                                )
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("battleInvites").child(uid).addValueEventListener(inviteListener!!)
    }

    // ── showBattleInviteDialog sets the flag itself at the top ────────────────
    private fun showBattleInviteDialog(invite: BattleInvite) {
        // This is the ONLY place isInviteDialogShowing is set to true
        if (isInviteDialogShowing || isFinishing) return
        isInviteDialogShowing = true

        val uid = auth.currentUser?.uid ?: return

        // Mark as seen so FriendsActivity doesn't show it again
        db.child("battleInvites").child(uid)
            .child(invite.inviteId).child("status").setValue("seen")

        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_battle_invite, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Make the background transparent for Claymorphism rounded corners!
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogView.findViewById<TextView>(R.id.tvInviterName).text = invite.fromUsername
        dialogView.findViewById<TextView>(R.id.tvTopicName).text = invite.topicName
        dialogView.findViewById<TextView>(R.id.tvQuestionCount).text =
            "${invite.questionCount} Questions"

        val ivAvatar = dialogView.findViewById<android.widget.ImageView>(R.id.ivInviterAvatar)
        ivAvatar.load(AvatarUtils.getAvatarUrl(invite.fromAvatarId)) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
            transformations(CircleCropTransformation())
        }

        dialogView.findViewById<Button>(R.id.btnAcceptInvite).setOnClickListener {
            isInviteDialogShowing = false
            acceptBattleInvite(invite)
            dialog.dismiss()
        }

        // FIX: Cast as TextView instead of Button!
        dialogView.findViewById<TextView>(R.id.btnRejectInvite).setOnClickListener {
            isInviteDialogShowing = false
            rejectBattleInvite(invite)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun acceptBattleInvite(invite: BattleInvite) {
        val myUid = auth.currentUser?.uid ?: return

        db.child("users").child(myUid).child("username")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val myUsername = snapshot.getValue(String::class.java) ?: "Player"
                    val player2 = RoomPlayer(uid = myUid, username = myUsername)

                    db.child("rooms").child(invite.roomCode)
                        .child("players").child("player2").setValue(player2)
                        .addOnSuccessListener {
                            db.child("rooms").child(invite.roomCode)
                                .child("status").setValue("ready")

                            db.child("battleInvites").child(myUid)
                                .child(invite.inviteId).removeValue()

                            db.child("battleInvites").child(invite.fromUid)
                                .child(invite.inviteId).setValue(
                                    mapOf(
                                        "inviteId" to invite.inviteId,
                                        "fromUid" to invite.fromUid,
                                        "fromUsername" to invite.fromUsername,
                                        "fromAvatarId" to invite.fromAvatarId,
                                        "roomCode" to invite.roomCode,
                                        "topicName" to invite.topicName,
                                        "topicId" to invite.topicId,
                                        "questionCount" to invite.questionCount,
                                        "status" to "accepted",
                                        "timestamp" to System.currentTimeMillis()
                                    )
                                )

                            startActivity(
                                Intent(this@HomeActivity, WaitingLobbyActivity::class.java).apply {
                                    putExtra("ROOM_CODE", invite.roomCode)
                                    putExtra("PLAYER_SLOT", "player2")
                                }
                            )
                        }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun rejectBattleInvite(invite: BattleInvite) {
        val myUid = auth.currentUser?.uid ?: return

        db.child("battleInvites").child(myUid)
            .child(invite.inviteId).removeValue()

        db.child("battleInvites").child(invite.fromUid)
            .child(invite.inviteId).setValue(
                mapOf(
                    "inviteId" to invite.inviteId,
                    "fromUid" to invite.fromUid,
                    "fromUsername" to invite.fromUsername,
                    "roomCode" to invite.roomCode,
                    "topicName" to invite.topicName,
                    "questionCount" to invite.questionCount,
                    "status" to "rejected",
                    "timestamp" to System.currentTimeMillis()
                )
            )

        db.child("rooms").child(invite.roomCode).removeValue()

        Toast.makeText(this, "Battle invite declined", Toast.LENGTH_SHORT).show()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        val uid = auth.currentUser?.uid ?: return
        inviteListener?.let {
            db.child("battleInvites").child(uid).removeEventListener(it)
        }
        db.child("users").child(uid).child("isOnline").setValue(false)
        db.child("users").child(uid).child("lastSeen")
            .setValue(System.currentTimeMillis())
    }
}