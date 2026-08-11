package com.puttvision.screen

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GitHubDeployClient(
    private val token: String,
    private val owner: String = "mercurial0416-lgtm",
    private val repo: String = "puttvision-screen",
    private val branch: String = "main"
) {
    data class Result(val commitSha: String, val commitUrl: String)

    fun deploy(bundle: DeployBundle, progress: (Int, String) -> Unit): Result {
        progress(2, "GitHub 확인")
        val ref = get("/repos/$owner/$repo/git/ref/heads/$branch")
        val headSha = ref.getJSONObject("object").getString("sha")

        val commit = get("/repos/$owner/$repo/git/commits/$headSha")
        val baseTree = commit.getJSONObject("tree").getString("sha")

        val treeEntries = JSONArray()
        bundle.files.forEachIndexed { index, file ->
            val pct = 5 + ((index + 1) * 70 / bundle.files.size.coerceAtLeast(1))
            progress(pct, "업로드 ${index + 1}/${bundle.files.size}")
            val encoded = Base64.encodeToString(file.bytes, Base64.NO_WRAP)
            val blob = post(
                "/repos/$owner/$repo/git/blobs",
                JSONObject().put("content", encoded).put("encoding", "base64")
            )
            treeEntries.put(
                JSONObject()
                    .put("path", file.path)
                    .put("mode", if (file.path.endsWith(".sh")) "100755" else "100644")
                    .put("type", "blob")
                    .put("sha", blob.getString("sha"))
            )
        }

        bundle.deletes.forEach { path ->
            treeEntries.put(
                JSONObject()
                    .put("path", path)
                    .put("mode", "100644")
                    .put("type", "blob")
                    .put("sha", JSONObject.NULL)
            )
        }

        progress(80, "커밋 준비")
        val newTree = post(
            "/repos/$owner/$repo/git/trees",
            JSONObject().put("base_tree", baseTree).put("tree", treeEntries)
        ).getString("sha")

        progress(88, "커밋 생성")
        val newCommit = post(
            "/repos/$owner/$repo/git/commits",
            JSONObject()
                .put("message", bundle.commitMessage)
                .put("tree", newTree)
                .put("parents", JSONArray().put(headSha))
        )
        val newSha = newCommit.getString("sha")

        progress(92, "이전 main 백업")
        saveBackupRef(headSha)

        progress(96, "main 반영")
        patch(
            "/repos/$owner/$repo/git/refs/heads/$branch",
            JSONObject().put("sha", newSha).put("force", false)
        )

        progress(100, "GitHub 반영 완료")
        return Result(newSha, "https://github.com/$owner/$repo/commit/$newSha")
    }


    fun rollback(progress: (Int, String) -> Unit): Result {
        progress(10, "롤백 백업 확인")
        val current = get("/repos/$owner/$repo/git/ref/heads/$branch")
            .getJSONObject("object").getString("sha")
        val backup = get("/repos/$owner/$repo/git/ref/heads/puttvision-backup")
            .getJSONObject("object").getString("sha")
        val backupCommit = get("/repos/$owner/$repo/git/commits/$backup")
        val backupTree = backupCommit.getJSONObject("tree").getString("sha")

        progress(55, "복구 커밋 생성")
        val restored = post(
            "/repos/$owner/$repo/git/commits",
            JSONObject()
                .put("message", "Rollback PuttVision one-tap deploy")
                .put("tree", backupTree)
                .put("parents", JSONArray().put(current))
        )
        val sha = restored.getString("sha")

        progress(85, "main 복구")
        patch(
            "/repos/$owner/$repo/git/refs/heads/$branch",
            JSONObject().put("sha", sha).put("force", false)
        )
        progress(100, "롤백 완료")
        return Result(sha, "https://github.com/$owner/$repo/commit/$sha")
    }

    private fun saveBackupRef(sha: String) {
        val created = runCatching {
            post(
                "/repos/$owner/$repo/git/refs",
                JSONObject()
                    .put("ref", "refs/heads/puttvision-backup")
                    .put("sha", sha)
            )
        }
        if (created.isFailure) {
            patch(
                "/repos/$owner/$repo/git/refs/heads/puttvision-backup",
                JSONObject().put("sha", sha).put("force", true)
            )
        }
    }

    fun verifyAccess(): String {
        val repoInfo = get("/repos/$owner/$repo")
        return repoInfo.getString("full_name")
    }

    private fun get(path: String): JSONObject = request("GET", path, null)
    private fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)
    private fun patch(path: String, body: JSONObject): JSONObject = request("PATCH", path, body)

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val connection = (URL("https://api.github.com$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 30_000
            doInput = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PuttVision-Screen-OneTapDeploy")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = if (stream != null) BufferedReader(InputStreamReader(stream)).use { it.readText() } else ""

        if (code !in 200..299) {
            val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
            error("GitHub $code: ${message?.takeIf { it.isNotBlank() } ?: text.take(180)}")
        }

        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
