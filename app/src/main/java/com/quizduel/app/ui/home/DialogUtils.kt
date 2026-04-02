package com.quizduel.app.ui.home

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import com.quizduel.app.R

object DialogUtils {

    /**
     * Shows the custom Indigo Rematch Request Dialog
     */
    fun showRematchRequestDialog(
        context: Context,
        onAccept: () -> Unit,
        onDecline: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_rematch, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false) // Force them to choose
            .create()

        // Clear default white background so our rounded indigo shape shows
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btnAccept).setOnClickListener {
            onAccept()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnDecline).setOnClickListener {
            onDecline()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Shows the custom Indigo Alert Dialog (for Opponent Left, Rematch Declined, etc.)
     */
    fun showAlertDialog(
        context: Context,
        title: String,
        message: String,
        iconEmoji: String = "⚠️",
        onConfirm: () -> Unit = {}
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_alert, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvAlertIcon).text = iconEmoji
        dialogView.findViewById<TextView>(R.id.tvAlertTitle).text = title
        dialogView.findViewById<TextView>(R.id.tvAlertMessage).text = message

        dialogView.findViewById<Button>(R.id.btnAlertOk).setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }

        dialog.show()
    }
}