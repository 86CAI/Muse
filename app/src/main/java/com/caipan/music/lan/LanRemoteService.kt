package com.caipan.music.lan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.caipan.music.MainActivity
import com.caipan.music.MuseApplication

class LanRemoteService : Service() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Muse LAN Remote", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Muse LAN Remote")
            .setContentText("已允许配对设备进行播放控制")
            .setContentIntent(open).setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
        startForeground(NOTIFICATION_ID, notification)
        (application as MuseApplication).lanRemoteManager.startHosting().onFailure { stopSelf() }
    }

    override fun onDestroy() {
        (application as MuseApplication).lanRemoteManager.stopHosting()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "muse_lan_remote"
        private const val NOTIFICATION_ID = 23
        fun start(context: Context) = context.startForegroundService(Intent(context, LanRemoteService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, LanRemoteService::class.java))
    }
}
