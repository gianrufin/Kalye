package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(raw: String): SemVer? {
            val cleaned = raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore("-")
                .substringBefore("+")
            if (cleaned.isBlank()) return null
            val parts = cleaned.split(".")
            if (parts.isEmpty() || parts.size > 3) return null
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            if (major < 0 || minor < 0 || patch < 0) return null
            return SemVer(major, minor, patch)
        }
    }
}

@Serializable
data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
data class GitHubTagDto(
    val name: String = ""
)

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val apkAssetName: String?,
    val apkSizeBytes: Long,
    val htmlUrl: String
)

sealed interface AppUpdateStatus {
    data object Idle : AppUpdateStatus
    data object Checking : AppUpdateStatus
    data class UpToDate(
        val currentVersion: String,
        val checkedAtMillis: Long
    ) : AppUpdateStatus

    data class UpdateAvailable(
        val currentVersion: String,
        val release: AppReleaseInfo
    ) : AppUpdateStatus

    data class Error(val message: String) : AppUpdateStatus
}

class AppUpdateChecker(
    private val repoOwner: String = BuildConfig.GITHUB_REPO_OWNER,
    private val repoName: String = BuildConfig.GITHUB_REPO_NAME,
    private val currentVersionProvider: () -> String = { BuildConfig.VERSION_NAME },
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    val repoSlug: String
        get() = "$repoOwner/$repoName"

    val releasesPageUrl: String
        get() = "https://github.com/$repoOwner/$repoName/releases"

    suspend fun checkForUpdates(): AppUpdateStatus = withContext(Dispatchers.IO) {
        val currentRaw = currentVersionProvider().ifBlank { "1.0.1" }
        val currentSemVer = SemVer.parse(currentRaw) ?: SemVer(1, 0, 1)

        val latestResult = fetchLatestRelease()
        val latest = latestResult.getOrElse { err ->
            return@withContext AppUpdateStatus.Error(
                err.message ?: "Unable to reach GitHub releases"
            )
        }

        if (latest == null) {
            return@withContext AppUpdateStatus.UpToDate(
                currentVersion = currentSemVer.toString(),
                checkedAtMillis = System.currentTimeMillis()
            )
        }

        val remoteSemVer = SemVer.parse(latest.versionName)
        if (remoteSemVer != null && remoteSemVer > currentSemVer) {
            AppUpdateStatus.UpdateAvailable(
                currentVersion = currentSemVer.toString(),
                release = latest
            )
        } else {
            AppUpdateStatus.UpToDate(
                currentVersion = currentSemVer.toString(),
                checkedAtMillis = System.currentTimeMillis()
            )
        }
    }

    private fun fetchLatestRelease(): Result<AppReleaseInfo?> = runCatching {
        val latestUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        val latestReq = buildRequest(latestUrl)
        client.newCall(latestReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                return@runCatching parseSingleReleaseJson(body, repoOwner, repoName)
            }
            if (resp.code != 404) {
                error("GitHub check failed (HTTP ${resp.code})")
            }
        }

        // Fallback to list of releases (if no non-prerelease latest exists)
        val listUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases?per_page=10"
        client.newCall(buildRequest(listUrl)).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                val fromList = parseReleasesListJson(body, repoOwner, repoName)
                if (fromList != null) return@runCatching fromList
            } else if (resp.code != 404) {
                error("GitHub releases check failed (HTTP ${resp.code})")
            }
        }

        // Final fallback to tags in case a version tag was pushed before release finished
        val tagsUrl = "https://api.github.com/repos/$repoOwner/$repoName/tags?per_page=10"
        client.newCall(buildRequest(tagsUrl)).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                return@runCatching parseHighestTagJson(body, repoOwner, repoName)
            }
            if (resp.code == 404) return@runCatching null
            error("GitHub tags check failed (HTTP ${resp.code})")
        }
    }

    private fun buildRequest(url: String): Request =
        Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Kalye-Android/${currentVersionProvider()}")
            .build()

    fun parseSingleReleaseJson(
        body: String,
        owner: String = repoOwner,
        repo: String = repoName
    ): AppReleaseInfo? {
        if (body.isBlank()) return null
        val dto = json.decodeFromString<GitHubReleaseDto>(body)
        if (dto.draft) return null
        return dto.toReleaseInfo(owner, repo)
    }

    fun parseReleasesListJson(
        body: String,
        owner: String = repoOwner,
        repo: String = repoName
    ): AppReleaseInfo? {
        if (body.isBlank()) return null
        val list = json.decodeFromString<List<GitHubReleaseDto>>(body)
        val best = list
            .filter { !it.draft && SemVer.parse(it.tagName) != null }
            .maxByOrNull { SemVer.parse(it.tagName) ?: SemVer(0, 0, 0) }
            ?: return null
        return best.toReleaseInfo(owner, repo)
    }

    fun parseHighestTagJson(
        body: String,
        owner: String = repoOwner,
        repo: String = repoName
    ): AppReleaseInfo? {
        if (body.isBlank()) return null
        val tags = json.decodeFromString<List<GitHubTagDto>>(body)
        val best = tags
            .mapNotNull { dto -> SemVer.parse(dto.name)?.let { ver -> dto.name to ver } }
            .maxByOrNull { it.second }
            ?: return null
        val tagName = best.first
        return AppReleaseInfo(
            tagName = tagName,
            versionName = best.second.toString(),
            releaseName = "Kalye $tagName",
            releaseNotes = "New version tag published in repository.",
            publishedAt = "",
            apkDownloadUrl = null,
            apkAssetName = null,
            apkSizeBytes = 0L,
            htmlUrl = "https://github.com/$owner/$repo/releases/tag/$tagName"
        )
    }

    private fun GitHubReleaseDto.toReleaseInfo(owner: String, repo: String): AppReleaseInfo {
        val cleanVer = SemVer.parse(tagName)?.toString() ?: tagName.removePrefix("v").removePrefix("V")
        val apkAsset = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        val fallbackPage = "https://github.com/$owner/$repo/releases/tag/$tagName"
        return AppReleaseInfo(
            tagName = tagName,
            versionName = cleanVer,
            releaseName = name.ifBlank { "Kalye $tagName" },
            releaseNotes = body.trim(),
            publishedAt = publishedAt.take(10),
            apkDownloadUrl = apkAsset?.browserDownloadUrl?.takeIf { it.isNotBlank() },
            apkAssetName = apkAsset?.name?.takeIf { it.isNotBlank() },
            apkSizeBytes = apkAsset?.size ?: 0L,
            htmlUrl = htmlUrl.ifBlank { fallbackPage }
        )
    }

    companion object {
        fun openUpdateInBrowser(context: Context, release: AppReleaseInfo, preferDirectApk: Boolean = true): Result<Unit> =
            runCatching {
                val targetUrl = if (preferDirectApk && !release.apkDownloadUrl.isNullOrBlank()) {
                    release.apkDownloadUrl
                } else {
                    release.htmlUrl
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
    }
}
