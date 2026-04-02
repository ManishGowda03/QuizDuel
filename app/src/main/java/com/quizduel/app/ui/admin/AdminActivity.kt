package com.quizduel.app.ui.admin

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.quizduel.app.R
import com.quizduel.app.databinding.ActivityAdminBinding
import com.quizduel.app.ui.auth.LoginActivity
import com.google.firebase.database.FirebaseDatabase
import com.quizduel.app.ui.home.HomeActivity

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseDatabase.getInstance().getReference("users")
                .child(uid).child("role")
                .get()
                .addOnSuccessListener { snapshot ->
                    val role = snapshot.getValue(String::class.java) ?: "player"
                    if (role != "admin") {
                        startActivity(Intent(this, HomeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    }
                }
        }

        setupViewPager()

        // Hook up the button to call your new custom glass dialog
        binding.btnLogout.setOnClickListener {
            showGlassLogoutDialog()
        }
    }

    private fun setupViewPager() {
        val adapter = AdminPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Topics"
                1 -> "Questions"
                2 -> "Users"
                3 -> "Leaderboard"
                else -> ""
            }
        }.attach()
    }

    private fun showGlassLogoutDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_logout)

        // This ensures the window behind our custom glass background is transparent
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Updated to cast to MaterialButton instead of TextView/CardView
        val btnCancel = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLogoutConfirm)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()

            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this@AdminActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        dialog.show()
    }
}

class AdminPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 4
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AdminTopicsFragment()
            1 -> AdminQuestionsFragment()
            2 -> AdminUsersFragment()
            3 -> AdminLeaderboardFragment()
            else -> AdminTopicsFragment()
        }
    }
}