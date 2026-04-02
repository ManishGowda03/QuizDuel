package com.quizduel.app // Make sure this matches your actual package name

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class QuizDuelApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // This single line tells Firebase to cache everything locally!
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)

        // Optional: Tells Firebase to keep the "topics" node synced to disk
        // even if the user hasn't opened that specific screen recently.
        FirebaseDatabase.getInstance().getReference("topics").keepSynced(true)
    }
}