package sahin.tethershare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.BindException

class ProxyService : LifecycleService() {

    private var proxyServer: HttpProxyServer? = null
    private var statsJob: Job? = null

    private var initialRxBytes: Long = 0
    private var initialTxBytes: Long = 0

    companion object {
        const val CHANNEL_ID = "ProxyServiceChannel"
        const val STATS_ACTION = "sahin.tethershare.STATS_UPDATE"
        const val EXTRA_CONNECTIONS = "connections"
        const val EXTRA_TOTAL_MB = "total_mb"
        const val EXTRA_SPEED_KBPS = "speed_kbps"
        const val ACTION_STOP = "STOP"
        const val EXTRA_PORT = "PORT"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val uid = packageManager.getApplicationInfo(packageName, 0).uid
        initialRxBytes = TrafficStats.getUidRxBytes(uid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0
        initialTxBytes = TrafficStats.getUidTxBytes(uid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080

        // If server is already running on the same port, just update notification if needed
        if (proxyServer?.port == port && proxyServer?.isRunning() == true) {
            return START_STICKY
        }

        // Stop existing server if port changed or we are restarting
        proxyServer?.stop()
        statsJob?.cancel()

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

        // Load authentication settings
        val authPrefs = AuthPreferences(this)
        val isAuthEnabled = authPrefs.isAuthEnabled
        val validCredentials = authPrefs.getValidCredentialsBase64()

        // Spin up the Proxy Server
        proxyServer = HttpProxyServer(port).apply {
            this.isAuthEnabled = isAuthEnabled
            this.validCredentialsBase64 = validCredentials
            onError = { e ->
                handleServerError(e, port)
            }
            start()
        }

        setupStatsTracker()

        return START_STICKY
    }

    private fun handleServerError(e: Exception, port: Int) {
        Handler(Looper.getMainLooper()).post {
            val message = if (e is BindException) {
                getString(R.string.error_port_in_use, port)
            } else {
                getString(R.string.error_starting_proxy)
            }
            Toast.makeText(this@ProxyService, message, Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun setupStatsTracker() {
        val uid = packageManager.getApplicationInfo(packageName, 0).uid
        
        statsJob = lifecycleScope.launch {
            var lastTotalBytes = (TrafficStats.getUidRxBytes(uid) + TrafficStats.getUidTxBytes(uid))
            
            while (isActive) {
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)

                val currentTotalBytes = rx + tx

                val sessionBytes = (rx - initialRxBytes) + (tx - initialTxBytes)
                val totalSessionMB = sessionBytes.toDouble() / (1024.0 * 1024.0)

                val speedBytes = currentTotalBytes - lastTotalBytes
                val speedKbps = (speedBytes.toDouble() / 1024.0).coerceAtLeast(0.0)

                lastTotalBytes = currentTotalBytes

                sendStatsBroadcast(totalSessionMB, speedKbps)
                
                delay(1000)
            }
        }
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
        statsJob?.cancel()
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
        manager?.createNotificationChannel(serviceChannel)
    }
}
