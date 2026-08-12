package com.puttvision.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * One-tap deployment surface. It is a second launcher entry and also an Android share target,
 * so a downloaded patch ZIP can be shared directly to "PuttVision Deploy".
 */
class DeployActivity : AppCompatActivity() {
    private lateinit var controller: OneTapDeployController
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var deployButton: Button
    private lateinit var updater: AppUpdater
    private val handler = Handler(Looper.getMainLooper())
    private var pendingSharedZip: Uri? = null

    private val zipPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controller.deploy(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updater = AppUpdater(this)
        pendingSharedZip = extractSharedZip(intent)
        buildUi()

        controller = OneTapDeployController(
            this,
            onProgress = { percent, message ->
                progress.progress = percent.coerceIn(0, 100)
                status.text = if (percent in 1..99) "$message · $percent%" else message
                deployButton.isEnabled = percent == 0 || percent == 100
            },
            onSuccess = { result ->
                progress.progress = 100
                status.text = "GitHub main 반영 완료 ✓\n${result.commitSha.take(10)}\nActions 빌드 시작"
                deployButton.text = "다른 ZIP 배포"
                deployButton.isEnabled = true
                showSuccess(result)
                handler.postDelayed({ updater.check(silent = true) }, 120_000L)
                handler.postDelayed({ updater.check(silent = true) }, 300_000L)
            },
            onReadyToPickZip = { consumeSharedOrPick() }
        )

        // A shared ZIP is staged only. Never mutate GitHub without an explicit tap in this Activity.
        if (pendingSharedZip != null) {
            status.text = "공유 ZIP 수신 · 배포 버튼을 눌러 확인하세요"
            deployButton.text = "수신 ZIP 확인 → 배포"
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSharedZip = extractSharedZip(intent)
        if (pendingSharedZip != null) {
            status.text = "공유 ZIP 수신 · 배포 버튼을 눌러 확인하세요"
            deployButton.text = "수신 ZIP 확인 → 배포"
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pvSdp(18), pvSdp(14), pvSdp(18), pvSdp(14))
            setBackgroundColor(Pv.ink)
        }

        val panel = pvPanel(radiusDp = Pv.rXl, padDp = 22).apply { gravity = Gravity.CENTER_HORIZONTAL }
        panel.addView(pvEyebrow("PUTTVISION · RELEASE CONSOLE"))
        panel.addView(TextView(this).apply {
            text = "Deploy Center"
            textSize = pvSp(28f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Pv.textHi)
        })
        panel.addView(TextView(this).apply {
            text = "ZIP 선택  →  PRIVATE MAIN  →  SIGNED BUILD  →  UPDATE"
            textSize = pvSp(Pv.body)
            setTextColor(Pv.textMid)
            gravity = Gravity.CENTER
            setPadding(0, pvSdp(6), 0, pvSdp(22))
        })

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        panel.addView(progress, LinearLayout.LayoutParams(-1, pvSdp(8)))

        status = TextView(this).apply {
            text = if (SecureTokenStore(this@DeployActivity).hasToken()) "GitHub 연결됨 · ZIP 선택 대기" else "최초 1회 GitHub 연결 필요"
            textSize = pvSp(13.5f)
            setTextColor(Pv.primary)
            gravity = Gravity.CENTER
            maxLines = 3
            setPadding(0, pvSdp(16), 0, pvSdp(16))
        }
        panel.addView(status, LinearLayout.LayoutParams(-1, -2))

        deployButton = pvButton("ZIP 선택 → 배포", PvButtonStyle.PRIMARY, textSp = 15f, scaled = true) { controller.start() }
        panel.addView(deployButton, LinearLayout.LayoutParams(-1, pvSdp(52)))

        val utilities = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        utilities.addView(pvButton("GitHub 연결", PvButtonStyle.SECONDARY, textSp = 9.5f, scaled = true) { controller.showTokenSetup() }, LinearLayout.LayoutParams(0, pvSdp(46), 1f))
        utilities.addView(pvButton("업데이트 확인", PvButtonStyle.SECONDARY, textSp = 9.5f, scaled = true) { updater.check(silent = false) }, LinearLayout.LayoutParams(0, pvSdp(46), 1f).apply { marginStart = pvSdp(8) })
        utilities.addView(pvButton("직전 배포 롤백", PvButtonStyle.GHOST, textSp = 9.5f, scaled = true) {
                pvMessageDialog(
                    title = "직전 배포로 롤백",
                    message = "현재 main 내용을 직전 ZIP 배포 직전 상태로 복구 커밋합니다. 계속할까요?",
                    positiveLabel = "롤백",
                    onPositive = { controller.rollback() },
                    negativeLabel = "취소"
                ).show()
        }, LinearLayout.LayoutParams(0, pvSdp(46), 1f).apply { marginStart = pvSdp(8) })
        panel.addView(utilities, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvSdp(10) })

        panel.addView(pvButton("퍼팅 화면 열기  →", PvButtonStyle.AMBER) {
            startActivity(Intent(this@DeployActivity, MainActivity::class.java))
        }, LinearLayout.LayoutParams(-1, pvSdp(46)).apply { topMargin = pvSdp(10) })

        panel.addView(TextView(this).apply {
            text = "팁: 다운로드한 ZIP에서 공유 → PuttVision Deploy를 고르면 파일 선택도 생략됨."
            textSize = 12f
            setTextColor(Pv.textLo)
            gravity = Gravity.CENTER
            setPadding(0, pvSdp(18), 0, 0)
        })

        root.addView(panel, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(Pv.ink)
            addView(root, android.widget.FrameLayout.LayoutParams(-1, -2))
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (::updater.isInitialized) updater.resumePendingInstallIfPossible()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::controller.isInitialized) controller.close()
        if (::updater.isInitialized) updater.close()
        super.onDestroy()
    }

    private fun consumeSharedOrPick() {
        val shared = pendingSharedZip
        if (shared != null) {
            pendingSharedZip = null
            controller.deploy(shared)
        } else {
            zipPicker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
        }
    }

    private fun extractSharedZip(source: Intent): Uri? {
        if (source.action != Intent.ACTION_SEND) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            source.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun showSuccess(result: GitHubDeployClient.Result) {
        val message = TextView(this).apply {
            text = "main 커밋 완료. Actions에서 signed APK를 자동 생성합니다. 빌드가 끝나면 업데이트 확인을 누르세요."
            textSize = Pv.body
            setTextColor(Pv.textMid)
            setLineSpacing(pvDp(3).toFloat(), 1f)
        }
        pvDialog(
            title = "배포 완료 ✓",
            content = message,
            dismissLabel = "닫기",
            extraActions = listOf("Actions 보기" to {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mercurial0416-lgtm/puttvision-screen/actions")))
            })
        ).show()
    }
}
