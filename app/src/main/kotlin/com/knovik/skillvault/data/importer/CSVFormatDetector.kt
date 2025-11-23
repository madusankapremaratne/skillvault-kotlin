package com.knovik.skillvault.data.importer

import com.opencsv.CSVReader
import timber.log.Timber
import java.io.Reader
import java.io.StringReader

/**
 * Detects and analyzes CSV file format.
 * Identifies column structure, delimiter type, and validates format.
 */
object CSVFormatDetector {

    /**
     * Detected CSV format information.
     */
    data class CSVFormat(
        val delimiter: Char,
        val headers: List<String>,
        val columnCount: Int,
        val estimatedRowCount: Int,
        val hasHeaders: Boolean,
        val formatType: FormatType
    )

    enum class FormatType {
        KAGGLE_RESUME_DATASET,  // Original format with id, name, email, etc.
        EXTENDED_RESUME_DATASET, // User's 35-column format
        CUSTOM                   // Other formats
    }

    /**
     * Detect CSV format from file content.
     * 
     * @param reader Reader for CSV content
     * @param sampleLines Number of lines to sample for detection (default: 100)
     * @return Detected CSV format
     */
    suspend fun detectFormat(
        reader: Reader,
        sampleLines: Int = 100
    ): Result<CSVFormat> {
        return try {
            // Try different delimiters
            val delimiters = listOf(',', '\t', ';', '|')
            var bestDelimiter = ','
            var bestScore = 0
            var headers = emptyList<String>()

            for (delimiter in delimiters) {
                reader.reset()
                val csvReader = CSVReader(reader)
                val firstLine = csvReader.readNext()
                
                if (firstLine != null && firstLine.size > bestScore) {
                    bestScore = firstLine.size
                    bestDelimiter = delimiter
                    headers = firstLine.map { it.trim() }
                }
            }

            // Reset and read with best delimiter
            reader.reset()
            val csvReader = CSVReader(reader)
            val allLines = mutableListOf<Array<String>>()
            var line: Array<String>?
            var count = 0

            while (csvReader.readNext().also { line = it } != null && count < sampleLines) {
                line?.let { allLines.add(it) }
                count++
            }

            if (allLines.isEmpty()) {
                return Result.failure(Exception("Empty CSV file"))
            }

            val detectedHeaders = allLines.first().map { it.trim() }
            val formatType = identifyFormatType(detectedHeaders)

            val format = CSVFormat(
                delimiter = bestDelimiter,
                headers = detectedHeaders,
                columnCount = detectedHeaders.size,
                estimatedRowCount = allLines.size - 1, // Subtract header row
                hasHeaders = true,
                formatType = formatType
            )

            Timber.d("Detected CSV format: $formatType with ${format.columnCount} columns")
            Result.success(format)

        } catch (e: Exception) {
            Timber.e(e, "Failed to detect CSV format")
            Result.failure(e)
        }
    }

    /**
     * Detect format from string content.
     */
    suspend fun detectFormatFromString(
        content: String,
        sampleLines: Int = 100
    ): Result<CSVFormat> {
        val reader = StringReader(content)
        reader.mark(content.length)
        return detectFormat(reader, sampleLines)
    }

    /**
     * Identify the type of CSV format based on headers.
     */
    private fun identifyFormatType(headers: List<String>): FormatType {
        val headerLower = headers.map { it.lowercase().replace("_", "").replace(" ", "") }

        // Check for extended format (user's 35-column dataset)
        val extendedMarkers = listOf(
            "address", "careerobjective", "educationalinstitutionname",
            "professionalcompanynames", "certificationproviders", "matchedscore"
        )
        val extendedMatches = extendedMarkers.count { marker ->
            headerLower.any { it.contains(marker) }
        }

        if (extendedMatches >= 4) {
            return FormatType.EXTENDED_RESUME_DATASET
        }

        // Check for Kaggle format
        val kaggleMarkers = listOf("id", "name", "email", "phone", "summary", "skills")
        val kaggleMatches = kaggleMarkers.count { marker ->
            headerLower.any { it.contains(marker) }
        }

        if (kaggleMatches >= 4) {
            return FormatType.KAGGLE_RESUME_DATASET
        }

        return FormatType.CUSTOM
    }

    /**
     * Validate that required columns exist for resume import.
     */
    fun validateRequiredColumns(format: CSVFormat): Result<Unit> {
        val headerLower = format.headers.map { it.lowercase() }

        return when (format.formatType) {
            FormatType.KAGGLE_RESUME_DATASET -> {
                val required = listOf("name", "skills")
                val missing = required.filter { req ->
                    !headerLower.any { it.contains(req) }
                }
                if (missing.isEmpty()) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Missing required columns: ${missing.joinToString()}"))
                }
            }
            FormatType.EXTENDED_RESUME_DATASET -> {
                // More flexible - just need some data
                if (format.columnCount >= 10) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Insufficient columns for extended format"))
                }
            }
            FormatType.CUSTOM -> {
                // Very flexible for custom formats
                if (format.columnCount >= 3) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("CSV must have at least 3 columns"))
                }
            }
        }
    }

    /**
     * Get a preview of the CSV data.
     */
    fun getPreview(
        reader: Reader,
        maxRows: Int = 5
    ): Result<List<Map<String, String>>> {
        return try {
            val csvReader = CSVReader(reader)
            val headers = csvReader.readNext()?.map { it.trim() } ?: emptyList()
            val preview = mutableListOf<Map<String, String>>()

            var count = 0
            var line: Array<String>?

            while (csvReader.readNext().also { line = it } != null && count < maxRows) {
                line?.let { values ->
                    val row = headers.mapIndexed { index, header ->
                        header to (if (index < values.size) values[index].trim() else "")
                    }.toMap()
                    preview.add(row)
                }
                count++
            }

            Result.success(preview)
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate preview")
            Result.failure(e)
        }
    }
}
