package com.puttvision.screen

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URI

/** Security and lifecycle policy for self-update. Pure Kotlin so it is regression-testable. */
object V49UpdatePolicy {
    const val MIN_APK_BYTES = 32L * 1024L
    const val MAX_APK_BYTES = 220L * 1024L * 1024L
    const val MAX_MANIFEST_BYTES = 256L * 1024L
    const val MAX_VERSION_NAME_CHARS = 48
    const val MAX_UPDATE_CACHE_FILES = 3
    const val MAX_UPDATE_CACHE_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    const val MAX_FUTURE_FILE_SKEW_MS = 10L * 60L * 1000L
    private const val PUBLIC_UPDATE_HOST = "razejagceyznnajioxgx.supabase.co"
    private const val PUBLIC_UPDATE_PATH_PREFIX = "/storage/v1/object/public/puttvision-update/"
    private const val GITHUB_RELEASE_HOST = "github.com"
    private const val GITHUB_RELEASE_PATH_PREFIX = "/mercurial0416-lgtm/puttvision-screen/releases/download/"
    private const val GITHUB_CONSUMER_APK = "puttvision-consumer.apk"

    data class ManifestCheck(val valid: Boolean, val reason: String? = null)
    data class CacheCleanup(val deleted: Int, val kept: Int)

    fun validateManifestUrl(url: String): ManifestCheck = validateHttpsUrl(url, "manifest")

    fun validateInfo(info: UpdateInfo, publicChannel: Boolean): ManifestCheck {
        if (info.versionCode <= 0) return ManifestCheck(false, "versionCode가 1 이상이어야 합니다")
        val name = info.versionName.trim()
        if (name.isBlank() || name.length > MAX_VERSION_NAME_CHARS || name.any { it.code < 0x20 }) {
            return ManifestCheck(false, "versionName 형식이 올바르지 않습니다")
        }
        val apk = validateHttpsUrl(info.apkUrl, "apk")
        if (!apk.valid) return apk
        if (publicChannel) {
            val publicApk = validatePublicApkUrl(info.apkUrl)
            if (!publicApk.valid) return publicApk
        }
        val sha = info.sha256?.trim().orEmpty()
        if (publicChannel && !isSha256(sha)) return ManifestCheck(false, "공개 업데이트 SHA-256이 없거나 잘못되었습니다")
        if (sha.isNotBlank() && !isSha256(sha)) return ManifestCheck(false, "SHA-256 형식이 잘못되었습니다")
        return ManifestCheck(true)
    }

    fun validatePublicApkUrl(url: String): ManifestCheck = runCatching {
        val uri = URI(url.trim())
        require(uri.scheme.equals("https", true)) { "공개 APK URL은 HTTPS여야 합니다" }
        require(uri.port == -1 || uri.port == 443) { "공개 APK URL은 기본 HTTPS 포트만 허용됩니다" }
        require(uri.userInfo == null) { "공개 APK URL에 userinfo를 넣을 수 없습니다" }
        require(uri.fragment == null) { "공개 APK URL에 fragment를 넣을 수 없습니다" }
        require(uri.rawQuery == null) { "공개 APK URL에 query를 넣을 수 없습니다" }

        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
        when {
            host.equals(PUBLIC_UPDATE_HOST, true) -> {
                require(path.startsWith(PUBLIC_UPDATE_PATH_PREFIX)) { "공개 APK 경로가 허용된 업데이트 버킷이 아닙니다" }
                require(path.substringAfterLast('/').endsWith(".apk", true)) { "공개 업데이트 파일은 APK여야 합니다" }
            }
            host.equals(GITHUB_RELEASE_HOST, true) -> {
                require(path.startsWith(GITHUB_RELEASE_PATH_PREFIX)) { "GitHub 공개 APK 경로가 PuttVision release가 아닙니다" }
                val tail = path.removePrefix(GITHUB_RELEASE_PATH_PREFIX).split('/')
                require(tail.size == 2) { "GitHub 공개 APK 경로 형식이 잘못되었습니다" }
                require(tail[0].matches(Regex("pv-[0-9]+"))) { "GitHub release tag 형식이 잘못되었습니다" }
                require(tail[1] == GITHUB_CONSUMER_APK) { "GitHub 공개 업데이트 파일명이 허용되지 않습니다" }
            }
            else -> error("공개 APK host가 허용된 업데이트 저장소가 아닙니다")
        }
        ManifestCheck(true)
    }.getOrElse { ManifestCheck(false, it.message ?: "공개 APK URL 형식 오류") }

    fun validateArtifactVersion(
        installedVersionCode: Long,
        manifestVersionCode: Long,
        candidateVersionCode: Long
    ): ManifestCheck {
        if (installedVersionCode < 0L) return ManifestCheck(false, "설치 버전을 읽을 수 없습니다")
        if (manifestVersionCode <= installedVersionCode) return ManifestCheck(false, "manifest가 현재 버전보다 새 버전이 아닙니다")
        if (candidateVersionCode != manifestVersionCode) {
            return ManifestCheck(false, "APK versionCode가 manifest와 일치하지 않습니다")
        }
        return ManifestCheck(true)
    }

    fun validateDownloadedApkSize(bytes: Long): ManifestCheck = when {
        bytes <= MIN_APK_BYTES -> ManifestCheck(false, "APK가 비정상적으로 작습니다")
        bytes > MAX_APK_BYTES -> ManifestCheck(false, "APK가 허용 크기를 초과합니다")
        else -> ManifestCheck(true)
    }

    fun validateContentLength(length: Long): ManifestCheck = when {
        length < 0L -> ManifestCheck(true)
        length == 0L -> ManifestCheck(false, "APK Content-Length가 0입니다")
        length > MAX_APK_BYTES -> ManifestCheck(false, "APK가 허용 크기를 초과합니다")
        else -> ManifestCheck(true)
    }

    fun limited(input: InputStream, maxBytes: Long = MAX_APK_BYTES): InputStream {
        require(maxBytes > 0L) { "stream 제한 크기는 1 byte 이상이어야 합니다" }
        return object : FilterInputStream(input) {
            private var readBytes = 0L
            private fun account(n: Int) {
                if (n <= 0) return
                readBytes += n
                if (readBytes > maxBytes) throw IllegalStateException("APK 다운로드가 허용 크기를 초과했습니다")
            }
            override fun read(): Int = super.read().also { if (it >= 0) account(1) }
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also(::account)
        }
    }

    fun cleanCache(dir: File, keep: File? = null, nowMs: Long = System.currentTimeMillis()): CacheCleanup {
        dir.mkdirs()
        val keepCanonical = runCatching { keep?.canonicalFile }.getOrNull()
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".apk", true) }.orEmpty()
        var deleted = 0
        files.filter { file ->
            val canonical = runCatching { file.canonicalFile }.getOrNull()
            val age = nowMs - file.lastModified()
            canonical != null && canonical != keepCanonical &&
                (age > MAX_UPDATE_CACHE_AGE_MS || age < -MAX_FUTURE_FILE_SKEW_MS)
        }.forEach { if (runCatching { it.delete() }.getOrDefault(false)) deleted++ }

        val remaining = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".apk", true) }
            ?.sortedByDescending { it.lastModified() }
            ?.toMutableList().orEmpty().toMutableList()
        while (remaining.count { runCatching { it.canonicalFile }.getOrNull() != keepCanonical } > MAX_UPDATE_CACHE_FILES) {
            val victim = remaining.lastOrNull { runCatching { it.canonicalFile }.getOrNull() != keepCanonical } ?: break
            if (runCatching { victim.delete() }.getOrDefault(false)) {
                deleted++
                remaining.remove(victim)
            } else break
        }
        val kept = dir.listFiles()?.count { it.isFile && it.name.endsWith(".apk", true) } ?: 0
        return CacheCleanup(deleted, kept)
    }

    fun pendingPathAllowed(cacheDir: File, rawPath: String): Boolean = runCatching {
        val updateRoot = File(cacheDir, "updates").canonicalFile
        val candidate = File(rawPath).canonicalFile
        candidate.isFile && (candidate.parentFile == updateRoot) && candidate.name.endsWith(".apk", true)
    }.getOrDefault(false)

    fun isUpgrade(installedVersionCode: Long, candidateVersionCode: Long): Boolean =
        installedVersionCode >= 0L && candidateVersionCode > installedVersionCode

    fun isSha256(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    private fun validateHttpsUrl(url: String, label: String): ManifestCheck = runCatching {
        val uri = URI(url.trim())
        require(uri.scheme.equals("https", true)) { "$label URL은 HTTPS여야 합니다" }
        require(!uri.host.isNullOrBlank()) { "$label URL host가 없습니다" }
        require(uri.userInfo == null) { "$label URL에 userinfo를 넣을 수 없습니다" }
        require(uri.fragment == null) { "$label URL에 fragment를 넣을 수 없습니다" }
        require(uri.port == -1 || uri.port in 1..65535) { "$label URL port가 잘못되었습니다" }
        ManifestCheck(true)
    }.getOrElse { ManifestCheck(false, it.message ?: "$label URL 형식 오류") }
}
