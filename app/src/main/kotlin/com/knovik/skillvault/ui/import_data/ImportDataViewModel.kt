package com.knovik.skillvault.ui.import_data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.knovik.skillvault.data.entity.Resume
import com.knovik.skillvault.data.importer.ImportResult
import com.knovik.skillvault.data.importer.ResumeCSVImporter
import com.knovik.skillvault.data.repository.ResumeRepository
import com.knovik.skillvault.service.EmbeddingIngestionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel for CSV import functionality.
 * Handles both URL and local file imports with progress tracking.
 */
@HiltViewModel
class ImportDataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val csvImporter: ResumeCSVImporter,
    private val resumeRepository: ResumeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /**
     * Import from URL.
     */
    fun importFromUrl(url: String) {
        if (url.isBlank()) {
            _uiState.value = ImportUiState.Error("URL cannot be empty")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = ImportUiState.Loading("Starting import...")

                csvImporter.importFromURL(url).collect { result ->
                    when (result) {
                        is ImportResult.Progress -> {
                            _uiState.value = ImportUiState.Loading(result.message)
                        }
                        is ImportResult.Success -> {
                            saveResumes(result.resumes)
                        }
                        is ImportResult.Error -> {
                            _uiState.value = ImportUiState.Error(result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to import from URL")
                _uiState.value = ImportUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Import from local file URI.
     */
    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = ImportUiState.Loading("Reading file...")

                val result = csvImporter.importFromUri(uri) { current, total ->
                    _uiState.value = ImportUiState.Loading("Importing: $current rows processed")
                }

                result.onSuccess { resumes ->
                    saveResumes(resumes)
                }.onFailure { error ->
                    _uiState.value = ImportUiState.Error(error.message ?: "Import failed")
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to import from URI")
                _uiState.value = ImportUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Save imported resumes to database and trigger embedding generation.
     */
    private suspend fun saveResumes(resumes: List<Resume>) {
        try {
            _uiState.value = ImportUiState.Loading("Saving ${resumes.size} resumes...")

            var savedCount = 0
            resumes.forEach { resume ->
                resumeRepository.insertOrUpdateResume(resume)
                savedCount++

                if (savedCount % 50 == 0) {
                    _uiState.value = ImportUiState.Loading("Saved $savedCount/${resumes.size} resumes")
                }
            }

            // Schedule background embedding generation
            scheduleEmbeddingIngestion()

            _uiState.value = ImportUiState.Success(
                message = "Successfully imported $savedCount resumes. Generating embeddings in background...",
                resumeCount = savedCount
            )

            Timber.d("Successfully saved $savedCount resumes, embedding generation scheduled")

        } catch (e: Exception) {
            Timber.e(e, "Failed to save resumes")
            _uiState.value = ImportUiState.Error("Failed to save: ${e.message}")
        }
    }

    /**
     * Schedule WorkManager task to generate embeddings for imported resumes.
     */
    private fun scheduleEmbeddingIngestion() {
        val ingestionWork = OneTimeWorkRequestBuilder<EmbeddingIngestionWorker>()
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "embedding_ingestion",
            ExistingWorkPolicy.REPLACE,
            ingestionWork
        )

        Timber.d("Embedding ingestion work scheduled via WorkManager")
    }

    /**
     * Reset state to idle.
     */
    fun resetState() {
        _uiState.value = ImportUiState.Idle
    }

    /**
     * Manually trigger embedding generation for all unembedded resumes.
     * Useful if automatic generation failed or user wants to regenerate.
     */
    fun generateEmbeddings() {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Loading("Scheduling embedding generation...")
            scheduleEmbeddingIngestion()
            _uiState.value = ImportUiState.Success(
                message = "Embedding generation started in background",
                resumeCount = 0
            )
        }
    }
}

/**
 * UI state for import screen.
 */
sealed class ImportUiState {
    object Idle : ImportUiState()
    data class Loading(val message: String) : ImportUiState()
    data class Success(val message: String, val resumeCount: Int) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}
