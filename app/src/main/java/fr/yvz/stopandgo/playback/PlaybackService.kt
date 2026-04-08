package fr.yvz.stopandgo.playback

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Foreground service that keeps the app process alive while audio is playing,
 * so playback can continue when the user backgrounds the app or locks the screen.
 *
 * The notification it shows is built by PlaybackEngine and represents the
 * current playback state (title, artist, artwork, controls).
 */
class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = PlaybackEngine.getInstance(applicationContext)
        val notification = engine.buildPlaybackNotification()
        if (notification != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    engine.notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(engine.notificationId, notification)
            }
        }
        // START_NOT_STICKY: don't restart if killed by system
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Remove the foreground notification when the service stops.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        super.onDestroy()
    }
}
