package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

enum class V16CompanionRole { OFF, HOST, COMPANION }

data class V16CompanionUiStatus(
    val role: V16CompanionRole,
    val host: String?,
    val view: V15CameraView,
    val peers: Int,
    val received: Long,
    val rejected: Long,
    val sessionCode: String?,
    val syncLabel: String?,
    val label: String
)

/**
 * User-facing layer for V15 LAN transport.
 * A secondary phone can run the normal PuttVision measurement pipeline and publish its completed
 * measurement to the primary phone. That keeps the capture stack identical on every phone.
 */
object V16CompanionLinkRuntime {
    @Volatile private var role = V16CompanionRole.OFF
    @Volatile private var view = V15CameraView.FACE_ON
    @Volatile private var host: String? = null
    @Volatile private var sessionCode: String? = null
    private var server: V15CompanionServer? = null
    private var client: V15CompanionClient? = null

    @Synchronized
    fun startHost(context: Context): Boolean {
        stop()
        val code = V28CompanionProtocol.newSessionCode()
        val created = V15CompanionServer(sessionCode = code)
        val ok = created.start()
        if (!ok) {
            created.close()
            return false
        }
        server = created
        sessionCode = code
        role = V16CompanionRole.HOST
        host = null
        V29NsdRuntime.advertise(context, code, created.status().port)
        V15CompanionRuntime.clear()
        return true
    }

    @Synchronized
    fun join(hostAddress: String, cameraView: V15CameraView, pairingCode: String): Boolean {
        val clean = hostAddress.trim().substringBefore(':')
        val code = pairingCode.trim().uppercase()
        if (clean.isBlank() || code.length < 6) return false
        stop()
        val created = V15CompanionClient(clean, sessionCode = code)
        if (!created.connect()) {
            created.close()
            return false
        }
        client = created
        role = V16CompanionRole.COMPANION
        view = cameraView
        host = clean
        sessionCode = code
        return true
    }

    fun publishIfCompanion(metrics: ShotMetrics): Boolean {
        if (role != V16CompanionRole.COMPANION) return false
        val c = synchronized(this) { client } ?: return false
        val measurement = V15CameraMeasurement(
            cameraId = android.os.Build.MODEL + "-" + view.name,
            view = view,
            metrics = metrics,
            confidence = metrics.confidence ?: .55,
            receivedAtMs = System.currentTimeMillis()
        )
        return c.send(measurement)
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        runCatching { client?.close() }
        V29NsdRuntime.stopAdvertising()
        server = null
        client = null
        host = null
        sessionCode = null
        role = V16CompanionRole.OFF
        V15CompanionRuntime.clear()
    }

    fun status(): V16CompanionUiStatus {
        val s = synchronized(this) { server?.status() }
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
        )
    }

    fun hostAddressLabel(): String {
        val s = synchronized(this) { server?.status() } ?: return "서버 꺼짐"
        val ip = s.localAddresses.firstOrNull() ?: "Wi‑Fi IP 없음"
        return "$ip:${s.port}"
    }

    fun viewLabel(v: V15CameraView): String = when (v) {
        V15CameraView.PRIMARY -> "메인"
        V15CameraView.FACE_ON -> "정면"
        V15CameraView.DOWN_THE_LINE -> "측면"
        V15CameraView.TOP -> "탑뷰"
    }
}

fun showV16CompanionDialog(context: Context) {
    val status = V16CompanionLinkRuntime.status()
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(18), context.pvDp(8), context.pvDp(18), context.pvDp(8))
    }

    fun text(value: String, size: Float, strong: Boolean = false, accent: Boolean = false) = TextView(context).apply {
        this.text = value
        textSize = context.pvSp(size)
        setTextColor(if (accent) Pv.primary else if (strong) Pv.textHi else Pv.textMid)
        if (strong) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    root.addView(text("MULTI PHONE CAMERA", 7f, true, true))
    root.addView(text("현재: ${status.label}", 11f, true).apply { setPadding(0, context.pvDp(6), 0, context.pvDp(6)) })
    root.addView(text("메인폰은 여러 보조폰 측정값을 신뢰도 가중치로 합칩니다. 모든 폰은 같은 샷을 동시에 촬영하세요.", 8f))

    if (status.role == V16CompanionRole.HOST) {
        root.addView(text(V16CompanionLinkRuntime.hostAddressLabel(), 18f, true, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, context.pvDp(14), 0, context.pvDp(8))
        })
        root.addView(text("PAIR ${status.sessionCode ?: "--------"}", 18f, true, true).apply { gravity = Gravity.CENTER })
        root.addView(text("보조폰에서 주소+PAIR 코드를 입력하세요. 연결 ${status.peers}대 · 수신 ${status.received}회 · 거부 ${status.rejected}회", 8f))
    }

    AlertDialog.Builder(context)
        .setTitle("멀티폰 카메라")
        .setView(root)
        .setPositiveButton("메인폰으로 시작") { _, _ ->
            val ok = V16CompanionLinkRuntime.startHost(context)
            Toast.makeText(context, if (ok) "메인폰 시작 · 보조폰에서 자동검색 가능" else "메인폰 서버 시작 실패", Toast.LENGTH_LONG).show()
        }
        .setNeutralButton("보조폰 연결") { _, _ -> showV16JoinCompanionDialog(context) }
        .setNegativeButton(if (status.role == V16CompanionRole.OFF) "닫기" else "연결 끄기") { _, _ ->
            if (status.role != V16CompanionRole.OFF) {
                V16CompanionLinkRuntime.stop()
                Toast.makeText(context, "멀티폰 연결 종료", Toast.LENGTH_SHORT).show()
            }
        }
        .show()
}

private fun showV16JoinCompanionDialog(context: Context) {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(18), context.pvDp(6), context.pvDp(18), context.pvDp(2))
    }
    val hostInput = EditText(context).apply {
        hint = "예: 192.168.0.12"
        inputType = InputType.TYPE_CLASS_PHONE
        setSingleLine(true)
    }
    root.addView(hostInput, LinearLayout.LayoutParams(-1, context.pvDp(50)))
    val codeInput = EditText(context).apply {
        hint = "PAIR 코드 8자리"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        setSingleLine(true)
    }
    root.addView(codeInput, LinearLayout.LayoutParams(-1, context.pvDp(50)))

    val discover = TextView(context).apply {
        text = "같은 Wi‑Fi에서 메인폰 자동검색"
        textSize = context.pvSp(10f)
        setTextColor(Pv.primary)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(context.pvDp(8), context.pvDp(8), context.pvDp(8), context.pvDp(8))
        setOnClickListener {
            text = "검색 중…"
            V29NsdRuntime.discover(context) { hosts ->
                post {
                    text = "같은 Wi‑Fi에서 메인폰 자동검색"
                    if (hosts.isEmpty()) {
                        Toast.makeText(context, "메인폰을 못 찾음 · 수동 주소/PAIR 입력 가능", Toast.LENGTH_LONG).show()
                    } else {
                        AlertDialog.Builder(context)
                            .setTitle("발견된 PuttVision")
                            .setItems(hosts.map { it.label }.toTypedArray()) { _, which ->
                                val found = hosts[which]
                                hostInput.setText(found.host)
                                codeInput.setText(found.pairCode)
                                Toast.makeText(context, "${found.serviceName} 선택됨", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("취소", null)
                            .show()
                    }
                }
            }
        }
    }
    root.addView(discover, LinearLayout.LayoutParams(-1, context.pvDp(48)))

    val views = listOf(
        V15CameraView.FACE_ON to "정면",
        V15CameraView.DOWN_THE_LINE to "측면",
        V15CameraView.TOP to "탑뷰"
    )
    var selected = V15CameraView.FACE_ON
    views.forEach { (v, label) ->
        val row = TextView(context).apply {
            text = if (v == selected) "● $label" else "○ $label"
            textSize = context.pvSp(10f)
            setTextColor(if (v == selected) Pv.primary else Pv.textHi)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.pvDp(10), 0, context.pvDp(10), 0)
            setOnClickListener {
                selected = v
                // Reopen to keep this deliberately small and dependency-free.
                Toast.makeText(context, "$label 카메라 선택", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(row, LinearLayout.LayoutParams(-1, context.pvDp(40)))
    }

    AlertDialog.Builder(context)
        .setTitle("보조폰 연결")
        .setView(root)
        .setPositiveButton("연결") { _, _ ->
            val ok = V16CompanionLinkRuntime.join(hostInput.text.toString(), selected, codeInput.text.toString())
            Toast.makeText(context, if (ok) "보조폰 연결됨 · ${V16CompanionLinkRuntime.viewLabel(selected)} · ${V16CompanionLinkRuntime.status().syncLabel ?: "SYNC"}" else "연결 실패 · 같은 Wi‑Fi/주소/PAIR 코드 확인", Toast.LENGTH_LONG).show()
        }
        .setNegativeButton("취소", null)
        .show()
}
