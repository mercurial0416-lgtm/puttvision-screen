package com.puttvision.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

class OneTapDeployController(
    private val activity: Activity,
    private val onProgress: (Int, String) -> Unit,
    private val onSuccess: (GitHubDeployClient.Result) -> Unit,
    private val onReadyToPickZip: () -> Unit
) {
    private val store = SecureTokenStore(activity)
    private val executor = Executors.newSingleThreadExecutor()

    fun start() {
        if (store.loadToken().isNullOrBlank()) showTokenSetup() else onReadyToPickZip()
    }

    fun deploy(uri: Uri) {
        val token = store.loadToken()
        if (token.isNullOrBlank()) {
            showTokenSetup()
            return
        }

        executor.execute {
            try {
                onUi { onProgress(1, "ZIP 읽는 중") }
                val bundle = DeployBundleReader.read(activity.contentResolver, uri)
                val result = GitHubDeployClient(token).deploy(bundle) { p, text ->
                    onUi { onProgress(p, text) }
                }
                onUi { onSuccess(result) }
            } catch (t: Throwable) {
                onUi {
                    onProgress(0, "배포 실패")
                    activity.pvMessageDialog(
                        title = "ZIP 배포 실패",
                        message = t.message ?: "알 수 없는 오류",
                        positiveLabel = "GitHub 재연결",
                        onPositive = { showTokenSetup() },
                        negativeLabel = "확인"
                    ).show()
                }
            }
        }
    }

    fun showTokenSetup() {
        val message = TextView(activity).apply {
            text = "puttvision-screen 전용 Fine-grained token을 한 번만 넣으세요.\n\n" +
                "Repository: puttvision-screen only\n" +
                "Permissions: Contents = Read and write, Workflows = Read and write\n\n" +
                "토큰은 Android Keystore로 암호화 저장됩니다."
            setTextColor(Pv.textMid)
            textSize = Pv.body
            setLineSpacing(activity.pvDp(3).toFloat(), 1f)
        }
        val input = EditText(activity).apply {
            hint = "github_pat_..."
            setHintTextColor(Pv.textLo)
            setTextColor(Pv.textHi)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            background = activity.pvRounded(Pv.surfaceHi, Pv.rMd, Pv.line)
            setPadding(activity.pvDp(12), activity.pvDp(10), activity.pvDp(12), activity.pvDp(10))
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(message)
            addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.pvDp(14) })
        }

        activity.pvDialog(
            title = "GitHub 최초 1회 연결",
            content = box,
            dismissLabel = "저장/확인",
            onDismissTap = {
                val value = input.text?.toString().orEmpty().trim()
                if (value.isNotBlank()) {
                    runCatching { store.saveToken(value) }
                        .onSuccess { verifyThenPick() }
                        .onFailure { showError(it.message ?: "토큰 저장 실패") }
                }
            },
            extraActions = listOf(
                "토큰 만들기" to {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new")))
                },
                "취소" to {}
            )
        ).show()
    }

    fun rollback() {
        val token = store.loadToken()
        if (token.isNullOrBlank()) {
            showTokenSetup()
            return
        }
        executor.execute {
            try {
                val result = GitHubDeployClient(token).rollback { p, text ->
                    onUi { onProgress(p, text) }
                }
                onUi { onSuccess(result) }
            } catch (t: Throwable) {
                onUi {
                    onProgress(0, "롤백 실패")
                    activity.pvMessageDialog("롤백 실패", t.message ?: "알 수 없는 오류").show()
                }
            }
        }
    }

    fun clearToken() {
        store.clear()
        onProgress(0, "GitHub 연결 해제")
    }

    private fun verifyThenPick() {
        val token = store.loadToken() ?: return
        executor.execute {
            try {
                onUi { onProgress(0, "GitHub 권한 확인") }
                GitHubDeployClient(token).verifyAccess()
                onUi {
                    onProgress(0, "GitHub 연결됨")
                    onReadyToPickZip()
                }
            } catch (t: Throwable) {
                store.clear()
                onUi { showError("GitHub 연결 실패: ${t.message}") }
            }
        }
    }

    private fun showError(message: String) {
        activity.pvMessageDialog("GitHub 연결 오류", message).show()
    }

    private fun onUi(block: () -> Unit) = activity.runOnUiThread(block)
}
