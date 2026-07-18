package sahin.tethershare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService

class ProxyService : LifecycleService() {

    private var proxyServer: HttpProxyServer? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusRunnable: Runnable

    private var initialRxBytes: Long = 0
    private var initialTxBytes: Long = 0

    companion object {
        const val CHANNEL_ID = "ProxyServiceChannel"
        const val STATS_ACTION = "sahin.tethershare.STATS_UPDATE"
        const val EXTRA_CONNECTIONS = "connections"
        const val EXTRA_TOTAL_MB = "total_mb"
        const val EXTRA_SPEED_KBPS = "speed_kbps"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Save initial TrafficStats to count data used *only* during this run session
        val uid = packageManager.getApplicationInfo(packageName, 0).uid
        initialRxBytes = TrafficStats.getUidRxBytes(uid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0
        initialTxBytes = TrafficStats.getUidTxBytes(uid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val action = intent?.action
        if (action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra("PORT", 8080) ?: 8080

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.proxy_active))
            .setContentText(getString(R.string.proxy_running_on_port, port))
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        // Spin up the Proxy Server
        proxyServer = HttpProxyServer(port).apply {
            start()
        }

        // Periodically calculate network speeds and data usage
        setupStatsTracker()

        return START_NOT_STICKY
    }

    private fun setupStatsTracker() {
        val uid = packageManager.getApplicationInfo(packageName, 0).uid

        statusRunnable = object : Runnable {
            private var lastTotalBytes = (TrafficStats.getUidRxBytes(uid) + TrafficStats.getUidTxBytes(uid))

            override fun run() {
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)

                val currentTotalBytes = rx + tx

                // Calculate total session size in Megabytes (MB)
                val sessionBytes = (rx - initialRxBytes) + (tx - initialTxBytes)
                val totalSessionMB = sessionBytes.toDouble() / (1024.0 * 1024.0)

                // Speed in Kilobytes per second (since we run once every 1 second)
                val speedBytes = currentTotalBytes - lastTotalBytes
                val speedKbps = (speedBytes.toDouble() / 1024.0).coerceAtLeast(0.0)

                lastTotalBytes = currentTotalBytes

                // Send values to UI
                sendStatsBroadcast(totalSessionMB, speedKbps)

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(statusRunnable)
    }

    private fun sendStatsBroadcast(totalMB: Double, speedKbps: Double) {
        val intent = Intent(STATS_ACTION).apply {
            putExtra(EXTRA_CONNECTIONS, proxyServer?.getActiveConnectionCount() ?: 0)
            putExtra(EXTRA_TOTAL_MB, totalMB)
            putExtra(EXTRA_SPEED_KBPS, speedKbps)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(statusRunnable)
        proxyServer?.stop()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }
}