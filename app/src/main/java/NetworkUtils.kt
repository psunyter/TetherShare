package sahin.tethershare

import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    /**
     * Retrieves a map of active network interfaces and their IPv4 addresses.
     * Guaranteed to return non-null Strings to satisfy Pair<String, String>.
     */
    fun getLocalIpAddresses(unknownFallback: String = "Unknown"): Map<String, String> {
        val addressesMap = mutableMapOf<String, String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                // Skip loopback (127.0.0.1) and inactive interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val displayName = networkInterface.displayName ?: networkInterface.name ?: unknownFallback
                val addresses = Collections.list(networkInterface.inetAddresses)

                for (address in addresses) {
                    val ip = address.hostAddress

                    // 1. Safe null check (Resolves the warning on line 16)
                    if (ip != null) {
                        // Check if it's an IPv4 address (does not contain a colon)
                        val isIPv4 = !ip.contains(":")
                        if (isIPv4) {
                            // 2. Explicitly maps non-null Strings (Resolves the Pair warning on line 23)
                            addressesMap[displayName] = ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return addressesMap
    }
}