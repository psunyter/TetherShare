package sahin.tethershare

import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class HttpProxyServer(private val port: Int) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    // Thread-safe counter for connected clients
    private val activeConnections = AtomicInteger(0)

    // Callbacks to notify UI/Service of changes
    var onConnectionCountChanged: ((Int) -> Unit)? = null

    fun getActiveConnectionCount(): Int = activeConnections.get()

    fun start() {
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch {
                        // Increment active connection count
                        val currentCount = activeConnections.incrementAndGet()
                        onConnectionCountChanged?.invoke(currentCount)

                        try {
                            handleClient(clientSocket)
                        } finally {
                            // Ensure count is decremented even if errors occur
                            val currentCountAfter = activeConnections.decrementAndGet()
                            onConnectionCountChanged?.invoke(currentCountAfter)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        scope.cancel()
    }

    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        clientSocket.use { client ->
            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()

            val headerBuilder = StringBuilder()
            val buffer = ByteArray(8192)
            val bytesRead = clientIn.read(buffer)
            if (bytesRead == -1) return@withContext

            headerBuilder.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
            val header = headerBuilder.toString()
            val lines = header.split("\r\n")
            if (lines.isEmpty()) return@withContext

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
                    val targetSocket = Socket(host, targetPort)
                    targetSocket.use { target ->
                        clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                        clientOut.flush()

                        val job1 = launch { bridgeStreams(clientIn, target.getOutputStream()) }
                        val job2 = launch { bridgeStreams(target.getInputStream(), clientOut) }
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
                        val targetSocket = Socket(host, targetPort)
                        targetSocket.use { target ->
                            val targetOut = target.getOutputStream()
                            targetOut.write(buffer, 0, bytesRead)
                            targetOut.flush()

                            val job1 = launch { bridgeStreams(clientIn, targetOut) }
                            val job2 = launch { bridgeStreams(target.getInputStream(), clientOut) }
                            joinAll(job1, job2)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun bridgeStreams(input: InputStream, output: OutputStream) {
        val tempBuffer = ByteArray(8192)
        try {
            var length: Int
            while (input.read(tempBuffer).also { length = it } != -1) {
                output.write(tempBuffer, 0, length)
                output.flush()
            }
        } catch (_: Exception) {
            // normal termination
        }
    }
}