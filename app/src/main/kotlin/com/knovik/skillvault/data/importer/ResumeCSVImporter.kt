package com.knovik.skillvault.data.importer

import android.content.Context
import com.knovik.skillvault.data.entity.Resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest

/**
 * Importer for resume data from CSV files.
 * Supports Kaggle Resume Dataset format.
 */
object ResumeCSVImporter {

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
 * Data class for import progress tracking.
 */
data class ImportProgress(
    val processedCount: Int,
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val isComplete: Boolean,
)
