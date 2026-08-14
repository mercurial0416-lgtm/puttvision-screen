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
    val rejectedMeasurements: Long = 0L,
    val receivedFeatureTracks: Long = 0L,
    val lastError: String? = null
)

/**
 * Tiny newline-delimited JSON transport for old phones used as extra putting cameras.
 * No cloud account or server is involved. The primary phone listens on the LAN and secondary
 * phones push compact metrics/feature tracks. The actual shot remains owned by the primary phone.
 */
class V15CompanionServer(
    private val requestedPort: Int = DEFAULT_PORT,
    private val sessionCode: String? = null,
    private val onMeasurement: (V15CameraMeasurement) -> Unit = { measurement ->
        V15CompanionRuntime.submit(measurement)
        V37FeatureFusionRuntime.submit(measurement)
    },
    private val onStatus: ((V15CompanionLinkStatus) -> Unit)? = null
) : Closeable {
    private val running = AtomicBoolean(false)
    private val io = Executors.newCachedThreadPool()
    private val clients = CopyOnWriteArrayList<Socket>()
    private val measurementGate = V43CompanionSequenceGate()
    private val trackGate = V43CompanionSequenceGate()
    @Volatile private var server: ServerSocket? = null
    @Volatile private var received = 0L
    @Volatile private var receivedTracks = 0L
    @Volatile private var rejected = 0L
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
                BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)).use { out ->
                    while (running.get() && !socket.isClosed) {
                        val line = try { reader.readLine() } catch (_: SocketTimeoutException) { continue } ?: break
                        if (line.length > MAX_MESSAGE_CHARS) { rejected++; publish(); continue }
                        val code = sessionCode
                        if (code == null) {
                            V15CompanionWire.decode(line)?.let { received++; onMeasurement(it); publish() }
                                ?: run { rejected++; publish() }
                            continue
                        }
                        if (V28CompanionProtocol.isSync(line)) {
                            val ack = V28CompanionProtocol.syncAck(line, code, System.currentTimeMillis())
                            if (ack == null) { rejected++; publish(); continue }
                            out.write(ack); out.newLine(); out.flush(); continue
                        }
                        if (V43FeatureTrackWire.isFeatureTrack(line)) {
                            // V50 intentionally allows feature tracks to arrive several seconds
                            // after their physical impact because HFR analysis happens before send.
                            val packet = V50FeatureTrackWire.decode(line, code)
                            if (packet == null || !trackGate.accept(packet.cameraId, packet.sequence)) {
                                rejected++; publish(); continue
                            }
                            V43RemoteFeatureTrackRuntime.publish(packet)
                            receivedTracks++
                            publish()
                            continue
                        }
                        val packet = V43CompanionWire.decodeMeasurement(line, code)
                        if (packet == null || !measurementGate.accept(packet.measurement.cameraId, packet.sequence)) {
                            rejected++; publish(); continue
                        }
                        received++
                        onMeasurement(packet.measurement)
                        publish()
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
        rejectedMeasurements = rejected,
        receivedFeatureTracks = receivedTracks,
        lastError = lastError
    )

    private fun publish() = onStatus?.invoke(status())

    override fun close() {
        if (!running.compareAndSet(true, false)) {
            V37FeatureFusionRuntime.clear()
            V43RemoteFeatureTrackRuntime.clear()
            return
        }
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        runCatching { server?.close() }
        server = null
        io.shutdownNow()
        measurementGate.clear()
        trackGate.clear()
        V37FeatureFusionRuntime.clear()
        V43RemoteFeatureTrackRuntime.clear()
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
    private val port: Int = V15CompanionServer.DEFAULT_PORT,
    private val sessionCode: String? = null
) : Closeable {
    private val lock = Any()
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var reader: BufferedReader? = null
    @Volatile private var clockSync: V28ClockSync? = null
    @Volatile private var lastClockSyncAtMs: Long = 0L
    private var measurementSequence = 0L
    private var featureSequence = 0L

    fun connect(timeoutMs: Int = 1800): Boolean = synchronized(lock) {
        if (socket?.isConnected == true && socket?.isClosed == false) return@synchronized true
        closeLocked()
        try {
            val s = Socket()
            s.tcpNoDelay = true
            s.keepAlive = true
            s.soTimeout = V42CompanionSyncPolicy.SOCKET_READ_TIMEOUT_MS
            s.connect(java.net.InetSocketAddress(host, port), timeoutMs.coerceIn(300, 5000))
            socket = s
            writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val code = sessionCode
            if (code != null && !syncLocked(code)) error("sync rejected")
            true
        } catch (_: Throwable) {
            closeLocked()
            false
        }
    }

    /** One transparent reconnect/retry; sequence replay protection makes an uncertain first flush safe. */
    fun send(measurement: V15CameraMeasurement): Boolean = synchronized(lock) {
        val sequence = ++measurementSequence
        repeat(2) {
            if (prepareConnectionLocked()) {
                val code = sessionCode
                val encoded = code?.let {
                    V43CompanionWire.encodeMeasurement(it, measurement, clockSync?.offsetMs ?: 0L, sequence)
                } ?: V15CompanionWire.encode(measurement)
                if (writeLineLocked(encoded)) return@synchronized true
            }
            closeLocked()
        }
        false
    }

    fun sendFeatureTrack(
        cameraId: String,
        view: V15CameraView,
        track: HfrFeatureTrack,
        capturedAtMs: Long
    ): Boolean = synchronized(lock) {
        val code = sessionCode ?: return@synchronized false
        val sequence = ++featureSequence
        repeat(2) {
            if (prepareConnectionLocked()) {
                val storedAt = V41HfrFeatureTrackRuntime.latestStoredAtMs
                val impactAt = V41HfrFeatureTrackRuntime.latestPublishedAtMs
                val eventAt = if (V50StereoTimePolicy.usableImpactTimestamp(impactAt, storedAt)) impactAt else capturedAtMs
                val packet = V43FeatureTrackPacket(
                    cameraId = cameraId,
                    view = view,
                    capturedAtMs = eventAt + (clockSync?.offsetMs ?: 0L),
                    sequence = sequence,
                    track = track
                )
                if (writeLineLocked(V43FeatureTrackWire.encode(code, packet))) return@synchronized true
            }
            closeLocked()
        }
        false
    }

    fun syncStatus(): V28ClockSync? = clockSync

    fun syncHealth(nowMs: Long = System.currentTimeMillis()): V43CompanionSyncHealth =
        V43CompanionSyncHealthPolicy.evaluate(clockSync, lastClockSyncAtMs, nowMs)

    override fun close() = synchronized(lock) { closeLocked() }

    private fun prepareConnectionLocked(): Boolean {
        if (!connect()) return false
        val code = sessionCode
        if (code != null && V42CompanionSyncPolicy.shouldRefresh(
                lastSyncAtMs = lastClockSyncAtMs,
                nowMs = System.currentTimeMillis(),
                current = clockSync
            )
        ) {
            if (!syncLocked(code)) return false
        }
        return true
    }

    private fun writeLineLocked(value: String): Boolean {
        val out = writer ?: return false
        return try {
            out.write(value)
            out.newLine()
            out.flush()
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun syncLocked(code: String): Boolean {
        val out = writer ?: return false
        val input = reader ?: return false
        return try {
            val t0 = System.currentTimeMillis()
            out.write(V28CompanionProtocol.syncRequest(code, t0))
            out.newLine()
            out.flush()
            val ack = input.readLine() ?: return false
            val t2 = System.currentTimeMillis()
            val sync = V28CompanionProtocol.parseSyncAck(ack, t0, t2) ?: return false
            if (!V42CompanionSyncPolicy.acceptable(sync)) return false
            clockSync = sync
            lastClockSyncAtMs = t2
            true
        } catch (_: SocketTimeoutException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun closeLocked() {
        runCatching { reader?.close() }
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        writer = null
        reader = null
        socket = null
        clockSync = null
        lastClockSyncAtMs = 0L
    }
}
