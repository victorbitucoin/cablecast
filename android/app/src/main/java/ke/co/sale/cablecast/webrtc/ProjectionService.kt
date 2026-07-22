package ke.co.sale.cablecast.webrtc

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/** Minimal foreground service so MediaProjection screen capture is allowed. */
class ProjectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val chId = "cablecast_cast"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(chId, "Casting", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = Notification.Builder(this, chId)
            .setContentTitle("CableCast")
            .setContentText("Casting your screen")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, notif, type) else startForeground(7, notif)
        return START_NOT_STICKY
    }
}
