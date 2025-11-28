package com.knovik.skillvault.data.importer

import android.content.Context
import android.net.Uri
import com.knovik.skillvault.data.entity.Resume
import com.opencsv.CSVReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.security.MessageDigest

/**
 * Importer for resume data from CSV files.
 * Supports multiple CSV formats including Kaggle and extended 35-column format.
 * Can import from local files or remote URLs.
 */
class ResumeCSVImporter(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    /**
     * Import resumes from a CSV file.
     * Expected columns: ID, name, email, phone, summary, skills, experience, education, certifications
     *
     * @param filePath Path to CSV file
     * @param onProgress Callback for import progress
     * @return List of parsed Resume objects
     */
    suspend fun importFromCSV(
        filePath: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<List<Resume>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resumes = mutableListOf<Resume>()
            val reader = BufferedReader(InputStreamReader(java.io.FileInputStream(filePath)))
            
            val startTime = System.currentTimeMillis()
            reader.use { input ->
                var line: String?
                var lineNumber = 0
                val headerLine = input.readLine()
                lineNumber++
                
                if (headerLine == null) {
                    return@withContext Result.failure(Exception("Empty CSV file"))
                }

                val headers = parseCSVLine(headerLine)
                val expectedColumns = setOf(
                    "id", "name", "email", "phone", 
                    "summary", "skills", "experience", "education", "certifications"
                )

                // Validate headers (case-insensitive)
                val headerLower = headers.map { it.lowercase() }
                
                Timber.d("CSV headers: ${headerLower.joinToString()}")

                while (input.readLine().also { line = it } != null) {
                    lineNumber++
                    if (line.isNullOrBlank()) continue

                    try {
                        val values = parseCSVLine(line!!)
                        val resume = parseResumeFromCSV(values, headers)
                        
                        // Skip duplicates
                        if (resume.textHash.isNotEmpty()) {
                            resumes.add(resume)
                        }

                        if (lineNumber % 100 == 0) {
                            onProgress(resumes.size, lineNumber)
                        }
                    } catch (e: Exception) {
                        Timber.w("Skipped invalid line $lineNumber: ${e.message}")
                    }
                }
            }
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            android.util.Log.d("EdgeScoutExperiment", "CSV_IMPORT_TIME, ${resumes.size}, $duration")

            Timber.d("Imported ${resumes.size} resumes from CSV")
            Result.success(resumes)
        } catch (e: Exception) {
            Timber.e(e, "Failed to import CSV")
            Result.failure(e)
        }
    }

    /**
     * Parse a CSV line handling quoted fields and escaping.
     */
    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]

            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++ // Skip next quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }

        result.add(current.toString().trim())
        return result
    }

    /**
     * Parse a single resume from CSV values.
     */
    private fun parseResumeFromCSV(values: List<String>, headers: List<String>): Resume {
        val headerMap = headers.mapIndexed { index, header -> 
            header.lowercase() to (if (index < values.size) values[index] else "")
        }.toMap()

        val rawText = values.joinToString(" ")
        val textHash = sha256(rawText)

        return Resume(
            resumeId = headerMap["id"] ?: "unknown_${System.currentTimeMillis()}",
            fullName = headerMap["name"] ?: "",
            email = headerMap["email"] ?: "",
            phoneNumber = headerMap["phone"] ?: "",
            summary = headerMap["summary"] ?: "",
            skills = headerMap["skills"] ?: "",
            experience = headerMap["experience"] ?: "",
            education = headerMap["education"] ?: "",
            certifications = headerMap["certifications"] ?: "",
            rawText = rawText,
            textHash = textHash,
            fileFormat = "text",
            sourceFile = "kaggle_import",
            processingStatus = "pending",
        )
    }

    /**
     * Calculate SHA-256 hash of text for duplicate detection.
     */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Import from JSON format (alternative to CSV).
     */
    suspend fun importFromJSON(
        jsonData: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<List<Resume>> = withContext(Dispatchers.IO) {
        return@withContext try {
            // TODO: Implement JSON parsing using Moshi or Gson
            Result.failure(Exception("JSON import not yet implemented"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to import JSON")
            Result.failure(e)
        }
    }

    /**
     * Import resumes from a URL with progress tracking.
     * 
     * @param url URL to CSV file
     * @param onProgress Callback for import progress
     * @return Flow of import progress with final result
     */
    fun importFromURL(
        url: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Flow<ImportResult> = flow {
        try {
            emit(ImportResult.Progress(0, 0, "Downloading CSV..."))

            val downloader = CSVDownloader(okHttpClient)
            val cacheDir = context.cacheDir
            val fileName = "imported_${System.currentTimeMillis()}.csv"
            val destinationFile = File(cacheDir, fileName)

            // Download file
            var downloadComplete = false
            downloader.downloadCSV(url, destinationFile).collect { progress ->
                if (progress.error != null) {
                    emit(ImportResult.Error(progress.error))
                    return@collect
                }
                
                emit(ImportResult.Progress(
                    progress.bytesDownloaded.toInt(),
                    progress.totalBytes.toInt(),
                    "Downloading: ${progress.percentage}%"
                ))
                
                downloadComplete = progress.isComplete
            }

            if (!downloadComplete) {
                emit(ImportResult.Error("Download failed"))
                return@flow
            }

            // Import from downloaded file
            emit(ImportResult.Progress(0, 0, "Parsing CSV..."))
            val result = importFromFileWithDetection(destinationFile, url, onProgress)
            
            result.onSuccess { resumes ->
                emit(ImportResult.Success(resumes))
            }.onFailure { error ->
                emit(ImportResult.Error(error.message ?: "Import failed"))
            }

            // Clean up temp file
            destinationFile.delete()

        } catch (e: Exception) {
            Timber.e(e, "Failed to import from URL")
            emit(ImportResult.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Import resumes from local file URI (from file picker).
     * 
     * @param uri Content URI from file picker
     * @param onProgress Callback for import progress
     * @return List of parsed Resume objects
     */
    suspend fun importFromUri(
        uri: Uri,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<List<Resume>> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Copy URI content to temp file
            val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.csv")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val result = importFromFileWithDetection(
                tempFile,
                uri.lastPathSegment ?: "local_file",
                onProgress
            )

            // Clean up
            tempFile.delete()

            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to import from URI")
            Result.failure(e)
        }
    }

    /**
     * Import from file with automatic format detection.
     */
    private suspend fun importFromFileWithDetection(
        file: File,
        sourceFile: String,
        onProgress: (current: Int, total: Int) -> Unit
    ): Result<List<Resume>> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Detect format
            // Detect format using BufferedReader which supports mark()
            val formatResult = BufferedReader(FileReader(file)).use { reader ->
                reader.mark(100000)
                CSVFormatDetector.detectFormat(reader, sampleLines = 100)
            }

            if (formatResult.isFailure) {
                return@withContext Result.failure(
                    formatResult.exceptionOrNull() ?: Exception("Format detection failed")
                )
            }

            val format = formatResult.getOrThrow()
            Timber.d("Detected format: ${format.formatType} with ${format.columnCount} columns")

            // Validate format
            val validationResult = CSVFormatDetector.validateRequiredColumns(format)
            if (validationResult.isFailure) {
                return@withContext Result.failure(
                    validationResult.exceptionOrNull() ?: Exception("Validation failed")
                )
            }

            // Import with detected format
            val resumes = mutableListOf<Resume>()
            val csvReader = CSVReader(FileReader(file))
            
            val startTime = System.currentTimeMillis()
            
            val headers = csvReader.readNext()?.map { it.trim() } ?: emptyList()
            var lineNumber = 0
            var line: Array<String>?

            while (csvReader.readNext().also { line = it } != null) {
                lineNumber++
                
                try {
                    line?.let { values ->
                        // Create map of column name to value
                        val rowMap = headers.mapIndexed { index, header ->
                            header to (if (index < values.size) values[index].trim() else "")
                        }.toMap()

                        // Map to Resume using flexible mapper
                        val resume = FlexibleCSVMapper.mapToResume(
                            rowMap,
                            format.formatType,
                            sourceFile
                        )

                        resumes.add(resume)
                    }

                    if (lineNumber % 50 == 0) {
                        onProgress(resumes.size, lineNumber)
                    }
                } catch (e: Exception) {
                    Timber.w("Skipped invalid line $lineNumber: ${e.message}")
                }
            }

            csvReader.close()
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            android.util.Log.d("EdgeScoutExperiment", "CSV_IMPORT_DETECTION_TIME, ${resumes.size}, $duration")

            Timber.d("Imported ${resumes.size} resumes from $sourceFile")
            Result.success(resumes)

        } catch (e: Exception) {
            Timber.e(e, "Failed to import from file")
            Result.failure(e)
        }
    }

    /**
     * Validate resume data before import.
     */
    fun validateResume(resume: Resume): Result<Resume> {
        return when {
            resume.fullName.isBlank() && resume.resumeId.isBlank() -> {
                Result.failure(Exception("Resume must have name or ID"))
            }
            resume.summary.isBlank() && resume.experience.isBlank() -> {
                Result.failure(Exception("Resume must have summary or experience"))
            }
            else -> Result.success(resume)
        }
    }
}

/**
 * Result type for import operations with progress tracking.
 */
sealed class ImportResult {
    data class Progress(
        val current: Int,
        val total: Int,
        val message: String
    ) : ImportResult()
    
    data class Success(val resumes: List<Resume>) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

/**
 * Data class for import progress tracking (legacy).
 */
data class ImportProgress(
    val processedCount: Int,
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val isComplete: Boolean,
)

