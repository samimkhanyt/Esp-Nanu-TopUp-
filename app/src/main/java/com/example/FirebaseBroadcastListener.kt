package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase

class FirebaseBroadcastListener(private val context: Context) {

    companion object {
        @Volatile
        private var isAlreadyListening = false
    }

    fun startListening() {
        if (isAlreadyListening) {
            Log.d("BroadcastListener", "FirebaseBroadcastListener is already listening, skipping duplicate.")
            return
        }
        isAlreadyListening = true

        try {
            FirebaseInitHelper.ensureInitialized(context)

            val database = try {
                FirebaseDatabase.getInstance("https://samim-firebase-default-rtdb.firebaseio.com")
            } catch (e: Throwable) {
                try {
                    FirebaseDatabase.getInstance()
                } catch (e2: Throwable) {
                    Log.e("BroadcastListener", "Failed to get FirebaseDatabase instance: ${e2.message}")
                    null
                }
            } ?: return

            // 1. Listen to live_notifications path (Main Notification Channel from Admin)
            val liveRef = database.getReference("live_notifications")

            liveRef.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    processLiveNotification(snapshot)
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    processLiveNotification(snapshot)
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {
                    Log.e("BroadcastListener", "Live notification error: ${error.message}")
                }
            })

            // 2. Listen to notifications/broadcast path
            val broadcastRef = database.getReference("notifications/broadcast")

            broadcastRef.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    processDataSnapshot(snapshot)
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    processDataSnapshot(snapshot)
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {
                    Log.e("BroadcastListener", "Database error: ${error.message}")
                }
            })

        } catch (t: Throwable) {
            Log.e("BroadcastListener", "Error starting Firebase broadcast listener: ${t.message}", t)
        }
    }

    private fun isNotificationAlreadyProcessed(uniqueKey: String): Boolean {
        if (uniqueKey.isEmpty()) return false
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val rawList = prefs.getString("processed_notif_keys_str", "") ?: ""
        val keys = rawList.split(",").toSet()
        return keys.contains(uniqueKey)
    }

    private fun markNotificationAsProcessed(uniqueKey: String) {
        if (uniqueKey.isEmpty()) return
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val rawList = prefs.getString("processed_notif_keys_str", "") ?: ""
        var keys = rawList.split(",").filter { it.isNotEmpty() }.toMutableList()

        if (!keys.contains(uniqueKey)) {
            keys.add(uniqueKey)
            if (keys.size > 1000) {
                keys = keys.takeLast(600).toMutableList()
            }
            prefs.edit().putString("processed_notif_keys_str", keys.joinToString(",")).apply()
        }
    }

    private fun getStringVal(snapshot: DataSnapshot, field: String): String? {
        val child = snapshot.child(field)
        if (!child.exists()) return null
        return child.value?.toString()
    }

    private fun getLongVal(snapshot: DataSnapshot, field: String): Long? {
        val child = snapshot.child(field)
        if (!child.exists()) return null
        val v = child.value
        return when (v) {
            is Long -> v
            is Int -> v.toLong()
            is Double -> v.toLong()
            is String -> v.toLongOrNull()
            else -> null
        }
    }

    private fun processLiveNotification(snapshot: DataSnapshot) {
        try {
            val key = snapshot.key ?: ""
            if (key.isEmpty()) return

            val uniqueKey = "live_$key"
            if (isNotificationAlreadyProcessed(uniqueKey)) {
                return
            }

            val title = getStringVal(snapshot, "title")
                ?: getStringVal(snapshot, "name")
                ?: "Esp TopUp"
            val message = getStringVal(snapshot, "message")
                ?: getStringVal(snapshot, "body")
                ?: getStringVal(snapshot, "text")
                ?: ""

            if (message.isEmpty()) return

            val logoUrl = getStringVal(snapshot, "logoUrl")
                ?: getStringVal(snapshot, "image")
                ?: getStringVal(snapshot, "imageUrl")
                ?: NotificationHelper.DEFAULT_LOGO_URL
            val target = getStringVal(snapshot, "target") ?: "all"
            val targetEmail = getStringVal(snapshot, "targetEmail")?.lowercase()?.trim() ?: ""
            val rawTime = getLongVal(snapshot, "clientTimestamp")
                ?: getLongVal(snapshot, "timestamp")
                ?: getLongVal(snapshot, "time")
                ?: 0L

            val timestampMs = if (rawTime in 1..9999999999L) rawTime * 1000L else rawTime
            val currentTime = System.currentTimeMillis()
            // Accept notifications created within the last 6 hours or with 0 timestamp
            val isRecent = (timestampMs == 0L) || (Math.abs(currentTime - timestampMs) < 6 * 60 * 60 * 1000L)
            val isTargetUser = (target == "all" || targetEmail.isEmpty() || targetEmail == "all" || isEmailForThisUser(targetEmail))

            // Mark processed immediately
            markNotificationAsProcessed(uniqueKey)

            if (isRecent && isTargetUser) {
                Log.d("BroadcastListener", "Showing notification for $uniqueKey: $title")
                NotificationHelper.showNotification(
                    context = context,
                    title = title,
                    body = message,
                    imageUrl = logoUrl
                )
            }
        } catch (e: Exception) {
            Log.e("BroadcastListener", "Error processing live notification: ${e.message}")
        }
    }

    private fun isEmailForThisUser(targetEmail: String): Boolean {
        try {
            val cleanTarget = targetEmail.lowercase().trim()
            if (cleanTarget.isEmpty() || cleanTarget == "all") return true

            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val savedEmail = prefs.getString("user_email", "")?.lowercase()?.trim() ?: ""
            val savedUid = prefs.getString("user_uid", "")?.lowercase()?.trim() ?: ""

            if (savedEmail.isEmpty() && savedUid.isEmpty()) {
                return false
            }

            if (savedEmail.isNotEmpty()) {
                if (savedEmail == cleanTarget || savedEmail.contains(cleanTarget) || cleanTarget.contains(savedEmail)) {
                    return true
                }
            }

            if (savedUid.isNotEmpty() && (savedUid == cleanTarget || cleanTarget == savedUid)) {
                return true
            }
        } catch (e: Exception) {
            Log.e("BroadcastListener", "Error checking target email: ${e.message}")
        }
        return false
    }

    private fun processDataSnapshot(snapshot: DataSnapshot) {
        try {
            val key = snapshot.key ?: ""
            if (key.isEmpty()) return

            val uniqueKey = "bcast_$key"
            if (isNotificationAlreadyProcessed(uniqueKey)) {
                return
            }

            val title = getStringVal(snapshot, "title")
                ?: getStringVal(snapshot, "name")
                ?: "Esp TopUp"

            val body = getStringVal(snapshot, "body")
                ?: getStringVal(snapshot, "message")
                ?: getStringVal(snapshot, "text")
                ?: snapshot.value?.toString()
                ?: ""

            if (body.isEmpty()) return

            val imageUrl = getStringVal(snapshot, "image")
                ?: getStringVal(snapshot, "imageUrl")
                ?: getStringVal(snapshot, "logoUrl")
                ?: NotificationHelper.DEFAULT_LOGO_URL

            markNotificationAsProcessed(uniqueKey)

            NotificationHelper.showNotification(
                context = context,
                title = title,
                body = body,
                imageUrl = imageUrl
            )
        } catch (e: Exception) {
            Log.e("BroadcastListener", "Error processing DataSnapshot: ${e.message}")
        }
    }
}
