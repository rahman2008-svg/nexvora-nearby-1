package com.example.nearby.network.socket

import com.example.nearby.network.protocol.NetworkPacket
import com.example.nearby.network.protocol.PacketValidator
import com.example.nearby.network.protocol.defaultJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

class SocketManager(
    private val scope: CoroutineScope
) {
    companion object {
        const val DEFAULT_PORT = 8988
        private const val FRAME_DELIMITER = "\n---END_NEXVORA_FRAME---\n"
    }

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null

    private val isRunning = AtomicBoolean(false)

    private val _incomingPackets = MutableSharedFlow<NetworkPacket>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<NetworkPacket> = _incomingPackets.asSharedFlow()

    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1)
    val connectionState: SharedFlow<Boolean> = _connectionState.asSharedFlow()

    /**
     * Starts TCP Server Socket (used by Wi-Fi Direct Group Owner).
     */
    fun startServer(port: Int = DEFAULT_PORT) {
        if (isRunning.getAndSet(true)) return
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                while (isActive && isRunning.get()) {
                    val clientSocket = serverSocket?.accept() ?: break
                    setupSocketConnection(clientSocket)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    _connectionState.tryEmit(false)
                }
            }
        }
    }

    /**
     * Connects to Group Owner TCP Server (used by Wi-Fi Direct Client).
     */
    fun connectToServer(hostAddress: String, port: Int = DEFAULT_PORT) {
        if (isRunning.getAndSet(true)) return
        scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket(hostAddress, port)
                setupSocketConnection(socket)
            } catch (e: Exception) {
                isRunning.set(false)
                _connectionState.tryEmit(false)
            }
        }
    }

    private fun setupSocketConnection(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = 45000 // 45s read timeout
        } catch (_: Exception) {}

        activeSocket?.close()
        activeSocket = socket

        writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        _connectionState.tryEmit(true)

        startPacketReader(socket)
        startHeartbeat(socket)
    }

    private fun startPacketReader(socket: Socket) {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val frameBuffer = StringBuilder()

                while (isActive && isRunning.get() && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    if (line == "---END_NEXVORA_FRAME---") {
                        val packetJson = frameBuffer.toString().trim()
                        frameBuffer.clear()
                        if (packetJson.isNotEmpty()) {
                            parseAndEmitPacket(packetJson)
                        }
                    } else {
                        frameBuffer.append(line).append("\n")
                    }
                }
            } catch (e: Exception) {
                // Socket disconnected, timed out, or read failure
            } finally {
                closeConnection()
            }
        }
    }

    private fun parseAndEmitPacket(jsonString: String) {
        try {
            val packet = defaultJson.decodeFromString<NetworkPacket>(jsonString)
            if (PacketValidator.validatePacket(packet)) {
                when (packet.packetType) {
                    com.example.nearby.network.protocol.PacketType.PING -> {
                        // Automatically respond with PONG
                        scope.launch(Dispatchers.IO) {
                            sendPacket(
                                NetworkPacket(
                                    packetId = "pong_${System.currentTimeMillis()}",
                                    packetType = com.example.nearby.network.protocol.PacketType.PONG,
                                    senderId = "HEARTBEAT"
                                )
                            )
                        }
                    }
                    com.example.nearby.network.protocol.PacketType.PONG -> {
                        // Heartbeat reply received, connection is healthy
                    }
                    com.example.nearby.network.protocol.PacketType.DISCONNECT -> {
                        closeConnection()
                    }
                    else -> {
                        _incomingPackets.tryEmit(packet)
                    }
                }
            }
        } catch (e: Exception) {
            // Malformed packet safely ignored to prevent crash
        }
    }

    /**
     * Sends a packet to the connected peer over TCP.
     */
    suspend fun sendPacket(packet: NetworkPacket): Boolean = withContext(Dispatchers.IO) {
        val currentWriter = writer ?: return@withContext false
        val socket = activeSocket ?: return@withContext false

        if (socket.isClosed || !socket.isConnected) return@withContext false

        return@withContext try {
            val serialized = defaultJson.encodeToString(NetworkPacket.serializer(), packet)
            synchronized(currentWriter) {
                currentWriter.write(serialized)
                currentWriter.write(FRAME_DELIMITER)
                currentWriter.flush()
            }
            true
        } catch (e: Exception) {
            closeConnection()
            false
        }
    }

    private fun startHeartbeat(socket: Socket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning.get() && !socket.isClosed) {
                delay(20_000)
                if (isRunning.get() && socket.isConnected && !socket.isClosed) {
                    val pingPacket = NetworkPacket(
                        packetId = "ping_${System.currentTimeMillis()}",
                        packetType = com.example.nearby.network.protocol.PacketType.PING,
                        senderId = "HEARTBEAT"
                    )
                    sendPacket(pingPacket)
                }
            }
        }
    }

    /**
     * Closes current socket and releases network resources.
     */
    fun closeConnection(sendDisconnectNotice: Boolean = false) {
        if (sendDisconnectNotice) {
            try {
                val disconnectPacket = NetworkPacket(
                    packetId = "disc_${System.currentTimeMillis()}",
                    packetType = com.example.nearby.network.protocol.PacketType.DISCONNECT,
                    senderId = "DISCONNECT"
                )
                val serialized = defaultJson.encodeToString(NetworkPacket.serializer(), disconnectPacket)
                writer?.let { w ->
                    synchronized(w) {
                        w.write(serialized)
                        w.write(FRAME_DELIMITER)
                        w.flush()
                    }
                }
            } catch (_: Exception) {}
        }

        isRunning.set(false)
        readerJob?.cancel()
        heartbeatJob?.cancel()

        try {
            writer?.close()
        } catch (_: Exception) {}
        writer = null

        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        _connectionState.tryEmit(false)
    }
}
