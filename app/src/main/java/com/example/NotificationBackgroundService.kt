package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

class NotificationBackgroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d("NotifService", "NotificationBackgroundService onCreate - starting foreground service")
        startForegroundServiceNotification()
        try {
            FirebaseBroadcastListener(applicationContext).startListening()
        } catch (e: Throwable) {
            Log.e("NotifService", "Error starting FirebaseBroadcastListener: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("NotifService", "NotificationBackgroundService onStartCommand")
        startForegroundServiceNotification()
        try {
            FirebaseBroadcastListener(applicationContext).startListening()
        } catch (e: Throwable) {
            Log.e("NotifService", "Error starting FirebaseBroadcastListener: ${e.message}")
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        try {
            NotificationHelper.createNotificationChannel(this)
            val notification = NotificationHelper.buildOngoingServiceNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        NotificationHelper.SERVICE_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (e: Throwable) {
                    startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e("NotifService", "Error starting foreground notification: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun startService(context: Context) {
            try {
                NotificationHelper.createNotificationChannel(context)
                val intent = Intent(context, NotificationBackgroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.e("NotifService", "Failed to start NotificationBackgroundService: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, NotificationBackgroundService::class.java)
                context.stopService(intent)
            } catch (e: Throwable) {
                Log.e("NotifService", "Failed to stop NotificationBackgroundService: ${e.message}")
            }
        }
    }
}

