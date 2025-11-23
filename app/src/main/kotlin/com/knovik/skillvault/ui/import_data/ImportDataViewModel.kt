package com.knovik.skillvault.ui.import_data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knovik.skillvault.data.entity.Resume
import com.knovik.skillvault.data.importer.ImportResult
import com.knovik.skillvault.data.importer.ResumeCSVImporter
import com.knovik.skillvault.data.repository.ResumeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for CSV import functionality.
 * Handles both URL and local file imports with progress tracking.
 */
@HiltViewModel
class ImportDataViewModel @Inject constructor(
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
     * Save imported resumes to database.
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

            _uiState.value = ImportUiState.Success(
                message = "Successfully imported $savedCount resumes",
                resumeCount = savedCount
            )

            Timber.d("Successfully saved $savedCount resumes")

        } catch (e: Exception) {
            Timber.e(e, "Failed to save resumes")
            _uiState.value = ImportUiState.Error("Failed to save: ${e.message}")
        }
    }

    /**
     * Reset state to idle.
     */
    fun resetState() {
        _uiState.value = ImportUiState.Idle
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
