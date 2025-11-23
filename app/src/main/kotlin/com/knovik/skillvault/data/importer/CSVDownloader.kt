package com.knovik.skillvault.data.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Downloads CSV files from remote URLs.
 * Provides progress tracking and error handling.
 */
class CSVDownloader(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024 // 50MB
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L
    }

    /**
     * Download progress state.
     */
    data class DownloadProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percentage: Int,
        val isComplete: Boolean,
        val error: String? = null
    )

    /**
     * Download CSV from URL with progress tracking.
     * 
     * @param url URL to download from
     * @param destinationFile File to save downloaded content
     * @return Flow of download progress
     */
    fun downloadCSV(
        url: String,
        destinationFile: File
    ): Flow<DownloadProgress> = flow {
        try {
            Timber.d("Starting download from: $url")

            // Validate URL
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                emit(DownloadProgress(0, 0, 0, false, "Invalid URL: must start with http:// or https://"))
                return@flow
            }

            // Create HTTP client with timeouts
            val client = okHttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadProgress(
                    0, 0, 0, false,
                    "Download failed: HTTP ${response.code}"
                ))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadProgress(0, 0, 0, false, "Empty response body"))
                return@flow
            }

            val contentLength = body.contentLength()

            // Check file size limit
            if (contentLength > MAX_FILE_SIZE_BYTES) {
                emit(DownloadProgress(
                    0, contentLength, 0, false,
                    "File too large: ${contentLength / 1024 / 1024}MB (max 50MB)"
                ))
                return@flow
            }

            // Download with progress
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(destinationFile)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesDownloaded = 0L
            var bytesRead: Int

            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    bytesDownloaded += bytesRead

                    val percentage = if (contentLength > 0) {
                        ((bytesDownloaded * 100) / contentLength).toInt()
                    } else {
                        0
                    }

                    emit(DownloadProgress(
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = contentLength,
                        percentage = percentage,
                        isComplete = false
                    ))
                }

                // Complete
                emit(DownloadProgress(
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = contentLength,
                    percentage = 100,
                    isComplete = true
                ))

                Timber.d("Download complete: $bytesDownloaded bytes")

            } finally {
                inputStream.close()
                outputStream.close()
            }

        } catch (e: Exception) {
            Timber.e(e, "Download failed")
            emit(DownloadProgress(
                0, 0, 0, false,
                "Download error: ${e.message}"
            ))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Download CSV to temporary cache directory.
     * 
     * @param url URL to download from
     * @param cacheDir Cache directory for temporary files
     * @return Flow of download progress with final file path
     */
    suspend fun downloadToCache(
        url: String,
        cacheDir: File
    ): Flow<DownloadProgress> {
        val fileName = "imported_${System.currentTimeMillis()}.csv"
        val destinationFile = File(cacheDir, fileName)

        // Ensure cache directory exists
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        return downloadCSV(url, destinationFile)
    }

    /**
     * Validate URL format.
     */
    fun validateURL(url: String): Result<Unit> {
        return when {
            url.isBlank() -> Result.failure(Exception("URL cannot be empty"))
            !url.startsWith("http://") && !url.startsWith("https://") -> {
                Result.failure(Exception("URL must start with http:// or https://"))
            }
            !url.endsWith(".csv", ignoreCase = true) -> {
                Result.failure(Exception("URL must point to a .csv file"))
            }
            else -> Result.success(Unit)
        }
    }
}
