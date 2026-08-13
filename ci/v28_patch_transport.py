from pathlib import Path
p=Path('app/src/main/java/com/puttvision/screen/V15CompanionLink.kt')
t=p.read_text(encoding='utf-8')
if 'rejectedMeasurements: Long' not in t:
    t=t.replace('''    val peers: Int,
    val receivedMeasurements: Long,
    val lastError: String? = null''','''    val peers: Int,
    val receivedMeasurements: Long,
    val rejectedMeasurements: Long = 0L,
    val lastError: String? = null''',1)
    t=t.replace('''class V15CompanionServer(
    private val requestedPort: Int = DEFAULT_PORT,
    private val onMeasurement:''','''class V15CompanionServer(
    private val requestedPort: Int = DEFAULT_PORT,
    private val sessionCode: String? = null,
    private val onMeasurement:''',1)
    t=t.replace('''    @Volatile private var received = 0L
    @Volatile private var lastError: String? = null''','''    @Volatile private var received = 0L
    @Volatile private var rejected = 0L
    @Volatile private var lastError: String? = null''',1)
    old='''    private fun readLoop(socket: Socket) {
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
    }'''
    new='''    private fun readLoop(socket: Socket) {
        try {
            BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)).use { reader ->
                BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)).use { out ->
                    while (running.get() && !socket.isClosed) {
                        val line = try { reader.readLine() } catch (_: SocketTimeoutException) { continue } ?: break
                        if (line.length > MAX_MESSAGE_CHARS) { rejected++; continue }
                        val code = sessionCode
                        if (code == null) {
                            V15CompanionWire.decode(line)?.let { received++; onMeasurement(it); publish() }
                            continue
                        }
                        if (V28CompanionProtocol.isSync(line)) {
                            val ack = V28CompanionProtocol.syncAck(line, code, System.currentTimeMillis())
                            if (ack == null) { rejected++; publish(); continue }
                            out.write(ack); out.newLine(); out.flush(); continue
                        }
                        val measurement = V28CompanionProtocol.decodeMeasurement(line, code)
                        if (measurement == null) { rejected++; publish(); continue }
                        received++; onMeasurement(measurement); publish()
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
    }'''
    if old not in t: raise SystemExit('server readLoop marker')
    t=t.replace(old,new,1)
    t=t.replace('''        peers = clients.count { !it.isClosed },
        receivedMeasurements = received,
        lastError = lastError''','''        peers = clients.count { !it.isClosed },
        receivedMeasurements = received,
        rejectedMeasurements = rejected,
        lastError = lastError''',1)
    t=t.replace('''class V15CompanionClient(
    private val host: String,
    private val port: Int = V15CompanionServer.DEFAULT_PORT
)''','''class V15CompanionClient(
    private val host: String,
    private val port: Int = V15CompanionServer.DEFAULT_PORT,
    private val sessionCode: String? = null
)''',1)
    t=t.replace('''    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null''','''    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var reader: BufferedReader? = null
    @Volatile private var clockSync: V28ClockSync? = null''',1)
    old='''            socket = s
            writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            true'''
    new='''            socket = s
            writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val code = sessionCode
            if (code != null) {
                val t0 = System.currentTimeMillis()
                writer!!.write(V28CompanionProtocol.syncRequest(code, t0)); writer!!.newLine(); writer!!.flush()
                val ack = reader!!.readLine() ?: error("sync closed")
                val t2 = System.currentTimeMillis()
                clockSync = V28CompanionProtocol.parseSyncAck(ack, t0, t2) ?: error("sync rejected")
            }
            true'''
    if old not in t: raise SystemExit('client connect marker')
    t=t.replace(old,new,1)
    old='''            out.write(V15CompanionWire.encode(measurement))
            out.newLine()'''
    new='''            val encoded = sessionCode?.let { V28CompanionProtocol.encodeMeasurement(it, measurement, clockSync?.offsetMs ?: 0L) }
                ?: V15CompanionWire.encode(measurement)
            out.write(encoded)
            out.newLine()'''
    if old not in t: raise SystemExit('client send marker')
    t=t.replace(old,new,1)
    t=t.replace('''    override fun close() = synchronized(lock) { closeLocked() }

    private fun closeLocked() {
        runCatching { writer?.close() }''','''    fun syncStatus(): V28ClockSync? = clockSync

    override fun close() = synchronized(lock) { closeLocked() }

    private fun closeLocked() {
        runCatching { reader?.close() }
        runCatching { writer?.close() }''',1)
    t=t.replace('''        writer = null
        socket = null''','''        writer = null
        reader = null
        socket = null
        clockSync = null''',1)
    p.write_text(t,encoding='utf-8'); print('V28 transport patched')
else:
    print('V28 transport current')
