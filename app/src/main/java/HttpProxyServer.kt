package sahin.tethershare

import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class HttpProxyServer(val port: Int) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    // Thread-safe counter for connected clients
    private val activeConnections = AtomicInteger(0)

    // Callbacks to notify UI/Service of changes
    var onConnectionCountChanged: ((Int) -> Unit)? = null
    var onError: ((Exception) -> Unit)? = null

    // Authentication
    var isAuthEnabled: Boolean = false
    var validCredentialsBase64: Set<String> = emptySet()

    companion object {
        private const val SOCKET_TIMEOUT = 30000 // 30 seconds
        private const val BUFFER_SIZE = 8192
    }

    fun getActiveConnectionCount(): Int = activeConnections.get()

    fun isRunning(): Boolean = isRunning

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                while (isRunning) {
                    val clientSocket = try {
                        serverSocket?.accept()
                    } catch (e: SocketException) {
                        if (isRunning) throw e else null
                    } ?: break

                    launch {
                        val currentCount = activeConnections.incrementAndGet()
                        onConnectionCountChanged?.invoke(currentCount)

                        try {
                            clientSocket.soTimeout = SOCKET_TIMEOUT
                            handleClient(clientSocket)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            val currentCountAfter = activeConnections.decrementAndGet()
                            onConnectionCountChanged?.invoke(currentCountAfter)
                            try {
                                if (!clientSocket.isClosed) clientSocket.close()
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    isRunning = false
                    onError?.invoke(e)
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        scope.cancel()
    }

    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        val clientIn = clientSocket.getInputStream()
        val clientOut = clientSocket.getOutputStream()

        val headerData = readHeader(clientIn) ?: return@withContext
        val header = String(headerData.bytes, 0, headerData.length, Charsets.UTF_8)
        val lines = header.split("\r\n")
        if (lines.isEmpty()) return@withContext

        // Check authentication if enabled
        if (isAuthEnabled) {
            val authHeader = lines.find { it.startsWith("Proxy-Authorization:", ignoreCase = true) }
            val isAuthorized = if (authHeader != null) {
                val base64Credentials = authHeader.substringAfter("Basic ").trim()
                validCredentialsBase64.contains(base64Credentials)
            } else {
                false
            }

            if (!isAuthorized) {
                clientOut.write(
                    ("HTTP/1.1 407 Proxy Authentication Required\r\n" +
                            "Proxy-Authenticate: Basic realm=\"TetherShare\"\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n\r\n").toByteArray()
                )
                clientOut.flush()
                return@withContext
            }
        }

        val requestLine = lines[0]
        val parts = requestLine.split(" ")
        if (parts.size < 2) return@withContext

        val method = parts[0].uppercase(Locale.US)
        val url = parts[1]

        if (method == "CONNECT") {
            val hostPort = url.split(":")
            val host = hostPort[0]
            val targetPort = if (hostPort.size > 1) hostPort[1].toInt() else 443

            try {
                Socket(host, targetPort).use { targetSocket ->
                    targetSocket.soTimeout = SOCKET_TIMEOUT
                    clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    clientOut.flush()

                    val job1 = launch { bridgeStreams(clientIn, targetSocket.getOutputStream(), targetSocket) }
                    val job2 = launch { bridgeStreams(targetSocket.getInputStream(), clientOut, clientSocket) }
                    joinAll(job1, job2)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            var host = ""
            var targetPort = 80

            val hostHeader = lines.find { it.startsWith("Host:", ignoreCase = true) }
            if (hostHeader != null) {
                val hostValue = hostHeader.substring(5).trim()
                val hostPort = hostValue.split(":")
                host = hostPort[0]
                if (hostPort.size > 1) targetPort = hostPort[1].toInt()
            }

            if (host.isNotEmpty()) {
                try {
                    Socket(host, targetPort).use { targetSocket ->
                        targetSocket.soTimeout = SOCKET_TIMEOUT
                        val targetOut = targetSocket.getOutputStream()
                        targetOut.write(headerData.bytes, 0, headerData.length)
                        targetOut.flush()

                        val job1 = launch { bridgeStreams(clientIn, targetOut, targetSocket) }
                        val job2 = launch { bridgeStreams(targetSocket.getInputStream(), clientOut, clientSocket) }
                        joinAll(job1, job2)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private class HeaderResult(val bytes: ByteArray, val length: Int)

    private fun readHeader(input: InputStream): HeaderResult? {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalRead = 0
        val endMarker = "\r\n\r\n".toByteArray()
        
        try {
            while (totalRead < BUFFER_SIZE) {
                val read = input.read(buffer, totalRead, BUFFER_SIZE - totalRead)
                if (read == -1) break
                totalRead += read
                
                // Check for end of headers
                if (indexOf(buffer, totalRead, endMarker) != -1) {
                    return HeaderResult(buffer, totalRead)
                }
            }
        } catch (_: SocketTimeoutException) {
            // Timeout reached
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return if (totalRead > 0) HeaderResult(buffer, totalRead) else null
    }

    private fun indexOf(data: ByteArray, length: Int, pattern: ByteArray): Int {
        if (pattern.isEmpty()) return 0
        for (i in 0 until (length - pattern.size + 1)) {
            var found = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private fun bridgeStreams(input: InputStream, output: OutputStream, targetSocket: Socket) {
        val tempBuffer = ByteArray(BUFFER_SIZE)
        try {
            var length: Int
            while (input.read(tempBuffer).also { length = it } != -1) {
                output.write(tempBuffer, 0, length)
                output.flush()
            }
        } catch (_: Exception) {
            // normal termination or timeout
        } finally {
            try {
                if (!targetSocket.isClosed) {
                    targetSocket.shutdownOutput() // Signal end of stream to the other side
                }
            } catch (_: Exception) {}
        }
    }
}
