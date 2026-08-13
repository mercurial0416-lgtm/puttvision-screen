package com.puttvision.screen

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean


data class V15CompanionLinkStatus(
    val running: Boolean,
    val port: Int,
    val localAddresses: List<String>,
    val peers: Int,
    val receivedMeasurements: Long,
    val lastError: String? = null
)

/**
 * Tiny newline-delimited JSON transport for old phones used as extra putting cameras.
 * No cloud account or server is involved. The primary phone listens on the LAN and secondary
 * phones push V15CompanionWire payloads. The actual shot remains owned by the primary phone.
 */
class V15CompanionServer(
    private val requestedPort: Int = DEFAULT_PORT,
    private val onMeasurement: (V15CameraMeasurement) -> Unit = V15CompanionRuntime::submit,
    private val onStatus: ((V15CompanionLinkStatus) -> Unit)? = null
) : Closeable {
    private val running = AtomicBoolean(false)
    private val io = Executors.newCachedThreadPool()
    private val clients = CopyOnWriteArrayList<Socket>()
    @Volatile private var server: ServerSocket? = null
    @Volatile private var received = 0L
    @Volatile private var lastError: String? = null

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        return try {
            val socket = ServerSocket(requestedPort).apply {
                reuseAddress = true
                soTimeout = 900
            }
            server = socket
            publish()
            io.execute { acceptLoop(socket) }
            true
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            running.set(false)
            publish()
            false
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get()) {
            try {
                val client = socket.accept().apply { soTimeout = 2500 }
                clients += client
                publish()
                io.execute { readLoop(client) }
            } catch (_: SocketTimeoutException) {
                // Periodically wake so close() can terminate without interrupt gymnastics.
            } catch (t: Throwable) {
                if (running.get()) lastError = t.message ?: t.javaClass.simpleName
            }
        }
    }

    private fun readLoop(socket: Socket) {
        try {
            BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)).use { reader ->
                while (running.get() && !socket.isClosed) {
                    val line = try { reader.readLine() } catch (_: SocketTimeoutException) { continue } ?: break
                    if (line.length > MAX_MESSAGE_CHARS) continue
                    V15CompanionWire.decode(line)?.let {
                        received++
                        onMeasurement(it)
                    }
                }
            }
        } catch (t: Throwable) {
            if (running.get()) lastError = t.message ?: t.javaClass.simpleName
        } finally {
            clients.remove(socket)
            runCatching { socket.close() }
            publish()
        }
    }

    fun status(): V15CompanionLinkStatus = V15CompanionLinkStatus(
        running = running.get(),
        port = server?.localPort ?: requestedPort,
        localAddresses = localIpv4Addresses(),
        peers = clients.count { !it.isClosed },
        receivedMeasurements = received,
        lastError = lastError
    )

    private fun publish() = onStatus?.invoke(status())

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        runCatching { server?.close() }
        server = null
        io.shutdownNow()
        publish()
    }

    companion object {
        const val DEFAULT_PORT = 39821
        private const val MAX_MESSAGE_CHARS = 8192

        fun localIpv4Addresses(): List<String> = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filter { address -> address is java.net.Inet4Address && !address.isLoopbackAddress }
                .map(InetAddress::getHostAddress)
                .filterNotNull()
                .distinct()
        }.getOrDefault(emptyList())
    }
}

class V15CompanionClient(
    private val host: String,
    private val port: Int = V15CompanionServer.DEFAULT_PORT
) : Closeable {
    private val lock = Any()
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null

    fun connect(timeoutMs: Int = 1800): Boolean = synchronized(lock) {
        if (socket?.isConnected == true && socket?.isClosed == false) return@synchronized true
        closeLocked()
        try {
            val s = Socket()
            s.tcpNoDelay = true
            s.keepAlive = true
            s.connect(java.net.InetSocketAddress(host, port), timeoutMs.coerceIn(300, 5000))
            socket = s
            writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            true
        } catch (_: Throwable) {
            closeLocked()
            false
        }
    }

    fun send(measurement: V15CameraMeasurement): Boolean = synchronized(lock) {
        if (!connect()) return@synchronized false
        val out = writer ?: return@synchronized false
        try {
            out.write(V15CompanionWire.encode(measurement))
            out.newLine()
            out.flush()
            true
        } catch (_: Throwable) {
            closeLocked()
            false
        }
    }

    override fun close() = synchronized(lock) { closeLocked() }

    private fun closeLocked() {
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        writer = null
        socket = null
    }
}
