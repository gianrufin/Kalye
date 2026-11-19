package com.example.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.example.BuildConfig
import com.example.data.model.MapLayerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

data class TileCoord(val z: Int, val x: Int, val y: Int)

data class RegionDownloadProgress(
    val downloaded: Int,
    val total: Int,
    val bytesDownloaded: Long,
    val isFinished: Boolean = false
)

class OfflineTileCacheManager(context: Context) {
    private val cacheRoot = File(context.applicationContext.filesDir, "offline_tiles").apply {
        if (!exists()) mkdirs()
    }

    @Volatile
    private var preferredProviderIndex: Int = 0

    @Volatile
    private var cacheSignatureVerified: Boolean = false

    private val memoryCache = object : LruCache<String, Bitmap>(96) {}

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    fun tileKey(@Suppress("UNUSED_PARAMETER") isDark: Boolean, z: Int, x: Int, y: Int): String {
        return "osm_street/$z/$x/$y"
    }

    fun getMemoryTile(isDark: Boolean, z: Int, x: Int, y: Int): Bitmap? {
        return memoryCache.get(tileKey(isDark, z, x, y))
    }

    suspend fun loadTile(layer: MapLayerType, isDark: Boolean, z: Int, x: Int, y: Int): Bitmap? = withContext(Dispatchers.IO) {
        ensureCacheMatchesCurrentConfig()
        val key = tileKey(isDark, z, x, y)
        memoryCache.get(key)?.let { return@withContext it }

        val file = tileFile(z, x, y)
        if (file.exists() && file.length() > 0L) {
            decodeFile(file)?.let { bmp ->
                memoryCache.put(key, bmp)
                return@withContext bmp
            }
        }

        if (layer == MapLayerType.OFFLINE) {
            return@withContext null
        }

        val bytes = fetchRemoteTileBytes(z, x, y) ?: return@withContext null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { it.write(bytes) }
        memoryCache.put(key, bmp)
        bmp
    }

    private fun ensureCacheMatchesCurrentConfig() {
        if (cacheSignatureVerified) return
        synchronized(this) {
            if (cacheSignatureVerified) return
            val sigFile = File(cacheRoot, ".tile_config_sig")
            val expectedSig = "v3_free_osm"
            val currentSig = if (sigFile.exists()) sigFile.readText().trim() else ""
            if (currentSig != expectedSig) {
                File(cacheRoot, "street_dark").deleteRecursively()
                File(cacheRoot, "street_light").deleteRecursively()
                memoryCache.evictAll()
                sigFile.writeText(expectedSig)
            }
            cacheSignatureVerified = true
        }
    }

    private fun decodeFile(file: File): Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    private fun tileFile(z: Int, x: Int, y: Int): File {
        return File(cacheRoot, "osm_street/$z/$x/$y.png")
    }

    private fun fetchRemoteTileBytes(z: Int, x: Int, y: Int): ByteArray? {
        val urls = buildFreeTileUrls(z, x, y)
        if (urls.isEmpty()) return null

        val startIndex = preferredProviderIndex.coerceIn(0, urls.lastIndex)
        val orderedIndices = listOf(startIndex) + urls.indices.filter { it != startIndex }

        for (idx in orderedIndices) {
            val request = Request.Builder()
                .url(urls[idx])
                .header(
                    "User-Agent",
                    "KalyeTracker/${BuildConfig.VERSION_NAME} (Android; https://github.com/${BuildConfig.GITHUB_REPO_OWNER}/${BuildConfig.GITHUB_REPO_NAME})"
                )
                .header("Accept", "image/png,image/*;q=0.9,*/*;q=0.8")
                .build()
            val bytes = try {
                client.newCall(request).execute().use { resp ->
                    val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
                    val isImage = contentType.isEmpty() || contentType.startsWith("image/")
                    if (resp.isSuccessful && isImage) {
                        resp.body?.bytes()?.takeIf { it.size > 128 }
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
            if (bytes != null) {
                preferredProviderIndex = idx
                return bytes
            }
        }
        return null
    }

    fun estimateTileList(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        minZoom: Int,
        maxZoom: Int
    ): List<TileCoord> {
        val tiles = mutableListOf<TileCoord>()
        val clampedMinZoom = minZoom.coerceIn(4, 16)
        val clampedMaxZoom = maxZoom.coerceIn(clampedMinZoom, 16)
        for (z in clampedMinZoom..clampedMaxZoom) {
            val (x1, y1) = latLonToTile(maxLat, minLon, z)
            val (x2, y2) = latLonToTile(minLat, maxLon, z)
            val startX = minOf(x1, x2)
            val endX = maxOf(x1, x2)
            val startY = minOf(y1, y2)
            val endY = maxOf(y1, y2)
            for (x in startX..endX) {
                for (y in startY..endY) {
                    tiles.add(TileCoord(z, x, y))
                    if (tiles.size >= 450) return tiles // Safety cap per region download
                }
            }
        }
        return tiles
    }

    fun downloadRegion(
        @Suppress("UNUSED_PARAMETER") isDark: Boolean,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        minZoom: Int,
        maxZoom: Int
    ): Flow<RegionDownloadProgress> = flow {
        val tiles = estimateTileList(minLat, maxLat, minLon, maxLon, minZoom, maxZoom)
        val total = tiles.size
        if (total == 0) {
            emit(RegionDownloadProgress(0, 0, 0L, isFinished = true))
            return@flow
        }
        ensureCacheMatchesCurrentConfig()
        var downloaded = 0
        var totalBytes = 0L
        val semaphore = Semaphore(4)
        for (tile in tiles) {
            val file = tileFile(tile.z, tile.x, tile.y)
            if (file.exists() && file.length() > 0L) {
                downloaded++
                totalBytes += file.length()
            } else {
                semaphore.withPermit {
                    val bytes = fetchRemoteTileBytes(tile.z, tile.x, tile.y)
                    if (bytes != null) {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { it.write(bytes) }
                        totalBytes += bytes.size
                    }
                    downloaded++
                }
            }
            if (downloaded % 5 == 0 || downloaded == total) {
                emit(RegionDownloadProgress(downloaded, total, totalBytes, isFinished = downloaded == total))
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getTotalDiskUsageBytes(): Long = withContext(Dispatchers.IO) {
        cacheRoot.walkTopDown()
            .filter { it.isFile && it.name != ".tile_config_sig" }
            .sumOf { it.length() }
    }

    suspend fun clearAllTiles() = withContext(Dispatchers.IO) {
        memoryCache.evictAll()
        cacheRoot.deleteRecursively()
        cacheRoot.mkdirs()
        cacheSignatureVerified = false
    }

    companion object {
        fun buildFreeTileUrls(z: Int, x: Int, y: Int): List<String> = listOf(
            "https://tile.openstreetmap.org/$z/$x/$y.png",
            "https://a.tile.openstreetmap.fr/osmfr/$z/$x/$y.png",
            "https://tile.openstreetmap.de/$z/$x/$y.png"
        )

        fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
            val n = 2.0.pow(zoom)
            val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n.toInt() - 1)
            val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
            val y = floor((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n.toInt() - 1)
            return x to y
        }

        fun tileToLatLon(x: Int, y: Int, zoom: Int): Pair<Double, Double> {
            val n = 2.0.pow(zoom)
            val lon = x / n * 360.0 - 180.0
            val latRad = atan(sinh(PI * (1.0 - 2.0 * y / n)))
            val lat = Math.toDegrees(latRad)
            return lat to lon
        }

        fun latLonToWorldPixel(lat: Double, lon: Double, zoom: Double, tileSize: Double = 256.0): Pair<Double, Double> {
            val scale = tileSize * 2.0.pow(zoom)
            val x = (lon + 180.0) / 360.0 * scale
            val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
            val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * scale
            return x to y
        }

        fun worldPixelToLatLon(px: Double, py: Double, zoom: Double, tileSize: Double = 256.0): Pair<Double, Double> {
            val scale = tileSize * 2.0.pow(zoom)
            val lon = px / scale * 360.0 - 180.0
            val n = PI - 2.0 * PI * py / scale
            val lat = Math.toDegrees(atan(0.5 * (kotlin.math.exp(n) - kotlin.math.exp(-n))))
            return lat to lon
        }
    }
}
