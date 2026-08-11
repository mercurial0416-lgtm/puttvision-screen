package com.puttvision.screen

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.widget.EditText
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
                    AlertDialog.Builder(activity)
                        .setTitle("ZIP 배포 실패")
                        .setMessage(t.message ?: "알 수 없는 오류")
                        .setPositiveButton("확인", null)
                        .setNeutralButton("GitHub 재연결") { _, _ -> showTokenSetup() }
                        .show()
                }
            }
        }
    }

    fun showTokenSetup() {
        val input = EditText(activity).apply {
            hint = "github_pat_..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }

        AlertDialog.Builder(activity)
            .setTitle("GitHub 최초 1회 연결")
            .setMessage(
                "puttvision-screen 전용 Fine-grained token을 한 번만 넣으세요.\n\n" +
                    "Repository: puttvision-screen only\n" +
                    "Permissions: Contents = Read and write, Workflows = Read and write\n\n" +
                    "토큰은 Android Keystore로 암호화 저장됩니다."
            )
            .setView(input)
            .setNegativeButton("취소", null)
            .setNeutralButton("토큰 만들기") { _, _ ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new")))
            }
            .setPositiveButton("저장/확인") { _, _ ->
                val value = input.text?.toString().orEmpty().trim()
                if (value.isNotBlank()) {
                    runCatching { store.saveToken(value) }
                        .onSuccess { verifyThenPick() }
                        .onFailure { showError(it.message ?: "토큰 저장 실패") }
                }
            }
            .show()
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
                    AlertDialog.Builder(activity)
                        .setTitle("롤백 실패")
                        .setMessage(t.message ?: "알 수 없는 오류")
                        .setPositiveButton("확인", null)
                        .show()
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
        AlertDialog.Builder(activity)
            .setTitle("GitHub 연결 오류")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun onUi(block: () -> Unit) = activity.runOnUiThread(block)
}
