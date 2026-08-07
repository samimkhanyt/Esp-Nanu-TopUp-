package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

class NotificationBackgroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        try {
            NotificationHelper.createNotificationChannel(this)
            val notification = NotificationHelper.buildOngoingServiceNotification(this)
            startForeground(1001, notification)
            Log.d("NotifService", "NotificationBackgroundService started foreground")
        } catch (e: Throwable) {
            Log.e("NotifService", "Error starting foreground service: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            NotificationHelper.createNotificationChannel(this)
            val notification = NotificationHelper.buildOngoingServiceNotification(this)
            startForeground(1001, notification)
        } catch (e: Throwable) {
            Log.e("NotifService", "Error in onStartCommand: ${e.message}")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun startService(context: Context) {
            try {
                val intent = Intent(context, NotificationBackgroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.e("NotifService", "Failed to start NotificationBackgroundService: ${e.message}")
            }
        }
    }
}

