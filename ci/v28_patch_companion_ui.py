from pathlib import Path
p=Path('app/src/main/java/com/puttvision/screen/V16CompanionUi.kt')
t=p.read_text(encoding='utf-8')
if 'sessionCode: String?' not in t:
    t=t.replace('''    val peers: Int,
    val received: Long,
    val label: String''','''    val peers: Int,
    val received: Long,
    val rejected: Long,
    val sessionCode: String?,
    val syncLabel: String?,
    val label: String''',1)
    t=t.replace('''    @Volatile private var host: String? = null
    private var server:''','''    @Volatile private var host: String? = null
    @Volatile private var sessionCode: String? = null
    private var server:''',1)
    t=t.replace('''        val created = V15CompanionServer()
        val ok = created.start()''','''        val code = V28CompanionProtocol.newSessionCode()
        val created = V15CompanionServer(sessionCode = code)
        val ok = created.start()''',1)
    t=t.replace('''        server = created
        role = V16CompanionRole.HOST''','''        server = created
        sessionCode = code
        role = V16CompanionRole.HOST''',1)
    t=t.replace('''    fun join(hostAddress: String, cameraView: V15CameraView): Boolean {
        val clean = hostAddress.trim().substringBefore(':')
        if (clean.isBlank()) return false''','''    fun join(hostAddress: String, cameraView: V15CameraView, pairingCode: String): Boolean {
        val clean = hostAddress.trim().substringBefore(':')
        val code = pairingCode.trim().uppercase()
        if (clean.isBlank() || code.length < 6) return false''',1)
    t=t.replace('''        val created = V15CompanionClient(clean)
        if (!created.connect())''','''        val created = V15CompanionClient(clean, sessionCode = code)
        if (!created.connect())''',1)
    t=t.replace('''        view = cameraView
        host = clean
        return true''','''        view = cameraView
        host = clean
        sessionCode = code
        return true''',1)
    t=t.replace('''        host = null
        role = V16CompanionRole.OFF''','''        host = null
        sessionCode = null
        role = V16CompanionRole.OFF''',1)
    old='''        val s = synchronized(this) { server?.status() }
        val label = when (role) {
            V16CompanionRole.OFF -> "꺼짐"
            V16CompanionRole.HOST -> "메인폰 · ${s?.peers ?: 0}대 연결"
            V16CompanionRole.COMPANION -> "보조폰 ${viewLabel(view)} · ${host ?: "연결중"}"
        }
        return V16CompanionUiStatus(
            role = role,
            host = host,
            view = view,
            peers = s?.peers ?: 0,
            received = s?.receivedMeasurements ?: 0L,
            label = label
        )'''
    new='''        val s = synchronized(this) { server?.status() }
        val sync = synchronized(this) { client?.syncStatus() }
        val label = when (role) {
            V16CompanionRole.OFF -> "꺼짐"
            V16CompanionRole.HOST -> "메인폰 · ${s?.peers ?: 0}대 · 거부 ${s?.rejectedMeasurements ?: 0}"
            V16CompanionRole.COMPANION -> "보조폰 ${viewLabel(view)} · ${sync?.label ?: "SYNC 중"}"
        }
        return V16CompanionUiStatus(
            role = role, host = host, view = view,
            peers = s?.peers ?: 0,
            received = s?.receivedMeasurements ?: 0L,
            rejected = s?.rejectedMeasurements ?: 0L,
            sessionCode = sessionCode,
            syncLabel = sync?.label,
            label = label
        )'''
    if old not in t: raise SystemExit('V16 status marker')
    t=t.replace(old,new,1)
    t=t.replace('''        root.addView(text("보조폰에서 위 주소를 입력하면 연결됩니다. 연결 ${status.peers}대 · 수신 ${status.received}회", 8f))''','''        root.addView(text("PAIR ${status.sessionCode ?: "--------"}", 18f, true, true).apply { gravity = Gravity.CENTER })
        root.addView(text("보조폰에서 주소+PAIR 코드를 입력하세요. 연결 ${status.peers}대 · 수신 ${status.received}회 · 거부 ${status.rejected}회", 8f))''',1)
    t=t.replace('''    root.addView(hostInput, LinearLayout.LayoutParams(-1, context.pvDp(50)))

    val views''','''    root.addView(hostInput, LinearLayout.LayoutParams(-1, context.pvDp(50)))
    val codeInput = EditText(context).apply {
        hint = "PAIR 코드 8자리"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        setSingleLine(true)
    }
    root.addView(codeInput, LinearLayout.LayoutParams(-1, context.pvDp(50)))

    val views''',1)
    t=t.replace('''            val ok = V16CompanionLinkRuntime.join(hostInput.text.toString(), selected)''','''            val ok = V16CompanionLinkRuntime.join(hostInput.text.toString(), selected, codeInput.text.toString())''',1)
    t=t.replace('''if (ok) "보조폰 연결됨 · ${V16CompanionLinkRuntime.viewLabel(selected)}" else "연결 실패 · 같은 Wi‑Fi와 주소 확인"''','''if (ok) "보조폰 연결됨 · ${V16CompanionLinkRuntime.viewLabel(selected)} · ${V16CompanionLinkRuntime.status().syncLabel ?: "SYNC"}" else "연결 실패 · 같은 Wi‑Fi/주소/PAIR 코드 확인"''',1)
    p.write_text(t,encoding='utf-8'); print('V28 companion UI patched')
else:
    print('V28 companion UI current')
