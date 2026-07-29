package sahin.tethershare

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ProxyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = ProxyService.isRunning
        if (isRunning) {
            val intent = Intent(this, ProxyService::class.java).apply {
                action = ProxyService.ACTION_STOP
            }
            startService(intent)
        } else {
            val intent = Intent(this, ProxyService::class.java).apply {
                action = "START"
                putExtra(ProxyService.EXTRA_PORT, 8080)
            }
            startForegroundService(intent)
        }
        // Small delay to allow service state to update before UI refresh
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isRunning = ProxyService.isRunning
        
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.subtitle = if (isRunning) getString(R.string.proxy_active_short) else getString(R.string.proxy_inactive_short)
        
        tile.updateTile()
    }
}
