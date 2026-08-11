package com.puttvision.screen

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

data class DeployFile(
    val path: String,
    val bytes: ByteArray
)

data class DeployBundle(
    val files: List<DeployFile>,
    val deletes: List<String>,
    val commitMessage: String
)

object DeployBundleReader {
    private const val MAX_TOTAL_BYTES = 25L * 1024L * 1024L
    private const val MAX_FILES = 500
    private const val MANIFEST = "puttvision-deploy.json"

    fun read(resolver: ContentResolver, uri: Uri): DeployBundle {
        val raw = mutableListOf<Pair<String, ByteArray>>()
        var total = 0L

        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue

                    val cleanName = normalize(entry.name)
                    if (cleanName.isBlank()) continue
                    if (raw.size >= MAX_FILES) error("ZIP 파일 수가 너무 많습니다.")

                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count <= 0) break
                        total += count
                        if (total > MAX_TOTAL_BYTES) error("ZIP이 25MB 제한을 넘었습니다.")
                        out.write(buffer, 0, count)
                    }
                    raw += cleanName to out.toByteArray()
                }
            }
        } ?: error("ZIP을 열 수 없습니다.")

        if (raw.isEmpty()) error("ZIP 안에 파일이 없습니다.")

        val manifestEntry = raw.firstOrNull { it.first.substringAfterLast('/') == MANIFEST }
        val manifest = manifestEntry?.let {
            JSONObject(it.second.toString(Charsets.UTF_8))
        }

        val prefix = manifest?.optString("stripPrefix")?.takeIf { it.isNotBlank() }
            ?: commonTopFolder(raw.map { it.first })

        val files = raw.mapNotNull { (name, bytes) ->
            if (name.substringAfterLast('/') == MANIFEST) return@mapNotNull null
            val stripped = stripPrefix(name, prefix)
            if (shouldIgnore(stripped)) return@mapNotNull null
            DeployFile(stripped, bytes)
        }

        if (files.isEmpty()) error("배포할 파일이 없습니다.")
        val duplicate = files.groupBy { it.path }.entries.firstOrNull { it.value.size > 1 }?.key
        if (duplicate != null) error("ZIP에 중복 파일 경로가 있습니다: $duplicate")

        val deletes = buildList {
            val array = manifest?.optJSONArray("delete")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val p = normalize(array.getString(i))
                    if (!shouldIgnore(p)) add(p)
                }
            }
        }

        val uniqueDeletes = deletes.distinct()
        val filePaths = files.mapTo(hashSetOf()) { it.path }
        val conflict = uniqueDeletes.firstOrNull { it in filePaths }
        if (conflict != null) error("같은 경로를 업로드와 삭제에 동시에 지정할 수 없습니다: $conflict")

        val message = manifest?.optString("message")?.takeIf { it.isNotBlank() }
            ?: "PuttVision one-tap deploy"

        return DeployBundle(files, uniqueDeletes, message.take(180))
    }

    private fun normalize(path: String): String {
        val fixed = path.replace('\\', '/').trimStart('/')
        require(!fixed.split('/').any { it == ".." }) { "잘못된 ZIP 경로입니다." }
        return fixed.split('/').filter { it.isNotBlank() && it != "." }.joinToString("/")
    }

    private fun commonTopFolder(paths: List<String>): String? {
        if (paths.isEmpty()) return null
        val first = paths.first().substringBefore('/', "")
        if (first.isBlank()) return null
        return if (paths.all { it.startsWith("$first/") }) "$first/" else null
    }

    private fun stripPrefix(path: String, prefix: String?): String {
        if (prefix.isNullOrBlank()) return path
        val normalized = prefix.replace('\\', '/').trimStart('/')
        return if (path.startsWith(normalized)) path.removePrefix(normalized).trimStart('/') else path
    }

    private fun shouldIgnore(path: String): Boolean {
        if (path.isBlank()) return true
        val p = "/$path/"
        return p.contains("/.git/") ||
            p.contains("/.gradle/") ||
            p.contains("/build/") ||
            path == "local.properties" ||
            path.endsWith("/local.properties")
    }
}
