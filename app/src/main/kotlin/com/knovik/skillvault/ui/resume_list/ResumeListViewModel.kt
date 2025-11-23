package com.knovik.skillvault.ui.resume_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knovik.skillvault.data.entity.Resume
import com.knovik.skillvault.data.repository.ResumeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for resume list.
 */
sealed class ResumeListUIState {
    data object Loading : ResumeListUIState()
    data object Empty : ResumeListUIState()
    data class Success(val resumes: List<Resume>) : ResumeListUIState()
    data class Error(val message: String) : ResumeListUIState()
}

/**
 * ViewModel for resume list screen.
 * Manages loading, filtering, and displaying resume data.
 */
@HiltViewModel
class ResumeListViewModel @Inject constructor(
    private val resumeRepository: ResumeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResumeListUIState>(ResumeListUIState.Loading)
    val uiState: StateFlow<ResumeListUIState> = _uiState.asStateFlow()

    private val _resumeCount = MutableStateFlow(0L)
    val resumeCount: StateFlow<Long> = _resumeCount.asStateFlow()

    private val _embeddingCount = MutableStateFlow(0L)
    val embeddingCount: StateFlow<Long> = _embeddingCount.asStateFlow()

    private val _storageStats = MutableStateFlow<Map<String, Long>>(emptyMap())
    val storageStats: StateFlow<Map<String, Long>> = _storageStats.asStateFlow()

    init {
        loadResumes()
        loadStatistics()
    }

    /**
     * Load all resumes from database.
     */
    fun loadResumes() {
        viewModelScope.launch {
            try {
                _uiState.value = ResumeListUIState.Loading
                
                val resumes = resumeRepository.getAllResumes()
                
                if (resumes.isEmpty()) {
                    _uiState.value = ResumeListUIState.Empty
                } else {
                    _uiState.value = ResumeListUIState.Success(resumes)
                }
                
                Timber.d("Loaded ${resumes.size} resumes")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load resumes")
                _uiState.value = ResumeListUIState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Delete a resume.
     */
    fun deleteResume(resumeId: Long) {
        viewModelScope.launch {
            try {
                resumeRepository.deleteResume(resumeId)
                Timber.d("Deleted resume $resumeId")
                loadResumes() // Refresh list
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete resume")
                _uiState.value = ResumeListUIState.Error("Failed to delete resume")
            }
        }
    }

    /**
     * Load statistics about stored data.
     */
    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                val count = resumeRepository.getResumeCount()
                val embeddingCount = resumeRepository.getEmbeddingCount()
                val stats = resumeRepository.getStorageStats()
                
                _resumeCount.value = count
                _embeddingCount.value = embeddingCount
                _storageStats.value = stats
                
                Timber.d("Statistics: Resumes=$count, Embeddings=$embeddingCount")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load statistics")
            }
        }
    }

    /**
     * Refresh data.
     */
    fun refresh() {
        loadResumes()
        loadStatistics()
    }
}
