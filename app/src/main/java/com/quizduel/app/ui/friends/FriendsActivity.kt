package com.quizduel.app.ui.friends

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.quizduel.app.data.model.FriendModel
import com.quizduel.app.data.model.FriendRequest
import com.quizduel.app.data.model.RoomPlayer
import com.quizduel.app.databinding.ActivityFriendsBinding
import com.quizduel.app.ui.duel.WaitingLobbyActivity
import com.quizduel.app.utils.NetworkUtils

class FriendsActivity : AppCompatActivity() {


    private lateinit var binding: ActivityFriendsBinding
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val friendsList = mutableListOf<FriendModel>()
    private val requestsList = mutableListOf<FriendRequest>()
    private val searchResults = mutableListOf<FriendModel>()

    private lateinit var friendsAdapter: FriendsAdapter
    private lateinit var requestsAdapter: FriendRequestAdapter
    private lateinit var searchAdapter: SearchResultAdapter

    private var friendsListener: ValueEventListener? = null
    private var requestsListener: ValueEventListener? = null

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(com.quizduel.app.R.color.clay_bg)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        setupRecyclerViews()
        setupSearch()
        loadFriends()
        loadFriendRequests()

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerViews() {
        friendsAdapter = FriendsAdapter(friendsList) { friend ->
            openInviteTopicPicker(friend)
        }
        binding.rvFriends.layoutManager = LinearLayoutManager(this)
        binding.rvFriends.adapter = friendsAdapter

        requestsAdapter = FriendRequestAdapter(
            requestsList,
            onAccept = { request -> acceptFriendRequest(request) },
            onReject = { request -> rejectFriendRequest(request) }
        )
        binding.rvRequests.layoutManager = LinearLayoutManager(this)
        binding.rvRequests.adapter = requestsAdapter

        searchAdapter = SearchResultAdapter(searchResults) { user ->
            sendFriendRequest(user)
        }
        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        binding.rvSearchResults.adapter = searchAdapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                searchRunnable?.let { searchHandler.removeCallbacks(it) }

                if (query.isEmpty()) {
                    binding.rvSearchResults.visibility = View.GONE
                    searchResults.clear()
                    searchAdapter.notifyDataSetChanged()
                    return
                }

                searchRunnable = Runnable {
                    if (!NetworkUtils.isInternetAvailable(this@FriendsActivity)) {
                        Toast.makeText(this@FriendsActivity, "No internet connection", Toast.LENGTH_SHORT).show()
                        return@Runnable
                    }
                    searchPlayers(query)
                }
                searchHandler.postDelayed(searchRunnable!!, 500)
            }
        })
    }

    private fun searchPlayers(query: String) {
        val myUid = auth.currentUser?.uid ?: return

        db.child("users").orderByChild("username")
            .startAt(query).endAt(query + "\uf8ff")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    searchResults.clear()
                    snapshot.children.forEach { child ->
                        val uid = child.child("uid").getValue(String::class.java) ?: return@forEach
                        if (uid == myUid) return@forEach  // skip self

                        // Skip admin accounts
                        val role = child.child("role").getValue(String::class.java) ?: "player"
                        if (role == "admin") return@forEach

                        val username = child.child("username").getValue(String::class.java) ?: ""
                        val avatarId = child.child("avatarId").getValue(Int::class.java) ?: 1
                        searchResults.add(FriendModel(uid, username, avatarId))
                    }
                    searchAdapter.notifyDataSetChanged()
                    binding.rvSearchResults.visibility =
                        if (searchResults.isEmpty()) View.GONE else View.VISIBLE
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun sendFriendRequest(user: FriendModel) {
        if (!NetworkUtils.isInternetAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }
        val myUid = auth.currentUser?.uid ?: return

        db.child("users").child(myUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val myUsername = snapshot.child("username")
                        .getValue(String::class.java) ?: "Player"
                    val myAvatarId = snapshot.child("avatarId")
                        .getValue(Int::class.java) ?: 1

                    val request = mapOf(
                        "uid" to myUid,
                        "username" to myUsername,
                        "avatarId" to myAvatarId,
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.child("friends").child(user.uid)
                        .child("receivedRequests").child(myUid)
                        .setValue(request)
                        .addOnSuccessListener {
                            db.child("friends").child(myUid)
                                .child("sentRequests").child(user.uid)
                                .setValue(true)
                            Toast.makeText(
                                this@FriendsActivity,
                                "Friend request sent to ${user.username}!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                this@FriendsActivity,
                                "Failed to send request",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadFriendRequests() {
        val myUid = auth.currentUser?.uid ?: return

        requestsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                requestsList.clear()
                snapshot.children.forEach { child ->
                    val uid = child.child("uid").getValue(String::class.java) ?: ""
                    val username = child.child("username").getValue(String::class.java) ?: ""
                    val avatarId = child.child("avatarId").getValue(Int::class.java) ?: 1
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    requestsList.add(FriendRequest(uid, username, avatarId, timestamp))
                }
                requestsAdapter.notifyDataSetChanged()
                binding.sectionRequests.visibility =
                    if (requestsList.isEmpty()) View.GONE else View.VISIBLE
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("friends").child(myUid).child("receivedRequests")
            .addValueEventListener(requestsListener!!)
    }

    private fun acceptFriendRequest(request: FriendRequest) {
        if (!NetworkUtils.isInternetAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }
        val myUid = auth.currentUser?.uid ?: return

        db.child("friends").child(myUid).child("friendsList").child(request.uid).setValue(true)
        db.child("friends").child(request.uid).child("friendsList").child(myUid).setValue(true)
        db.child("friends").child(myUid).child("receivedRequests").child(request.uid).removeValue()
        db.child("friends").child(request.uid).child("sentRequests").child(myUid).removeValue()

        Toast.makeText(this, "${request.username} is now your friend!", Toast.LENGTH_SHORT).show()
    }

    private fun rejectFriendRequest(request: FriendRequest) {
        if (!NetworkUtils.isInternetAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }
        val myUid = auth.currentUser?.uid ?: return

        db.child("friends").child(myUid).child("receivedRequests").child(request.uid).removeValue()
        db.child("friends").child(request.uid).child("sentRequests").child(myUid).removeValue()

        Toast.makeText(this, "Request rejected", Toast.LENGTH_SHORT).show()
    }

    private fun loadFriends() {
        val myUid = auth.currentUser?.uid ?: return

        friendsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val friendUids = snapshot.children.map { it.key ?: "" }.filter { it.isNotEmpty() }

                if (friendUids.isEmpty()) {
                    friendsList.clear()
                    friendsAdapter.notifyDataSetChanged()
                    binding.layoutEmptyFriends.visibility = View.VISIBLE
                    return
                }

                binding.layoutEmptyFriends.visibility = View.GONE
                friendsList.clear()

                var loaded = 0
                friendUids.forEach { uid ->
                    db.child("users").child(uid)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userSnap: DataSnapshot) {
                                val username = userSnap.child("username")
                                    .getValue(String::class.java) ?: ""
                                val avatarId = userSnap.child("avatarId")
                                    .getValue(Int::class.java) ?: 1
                                val isOnline = userSnap.child("isOnline")
                                    .getValue(Boolean::class.java) ?: false
                                val lastSeen = userSnap.child("lastSeen")
                                    .getValue(Long::class.java) ?: 0L

                                friendsList.add(FriendModel(uid, username, avatarId, isOnline, lastSeen))
                                loaded++

                                if (loaded == friendUids.size) {
                                    friendsList.sortByDescending { it.isOnline }
                                    friendsAdapter.notifyDataSetChanged()
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        db.child("friends").child(myUid).child("friendsList")
            .addValueEventListener(friendsListener!!)
    }

    private fun openInviteTopicPicker(friend: FriendModel) {
        if (!NetworkUtils.isInternetAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, InviteTopicPickerActivity::class.java).apply {
            putExtra("FRIEND_UID", friend.uid)
            putExtra("FRIEND_USERNAME", friend.username)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        val uid = auth.currentUser?.uid ?: return
        friendsListener?.let {
            db.child("friends").child(uid).child("friendsList").removeEventListener(it)
        }
        requestsListener?.let {
            db.child("friends").child(uid).child("receivedRequests").removeEventListener(it)
        }
    }
}