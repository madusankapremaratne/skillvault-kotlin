package com.knovik.skillvault.ui.resume_details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knovik.skillvault.data.entity.Resume
import com.knovik.skillvault.data.repository.ResumeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ResumeDetailsUiState {
    data object Loading : ResumeDetailsUiState()
    data class Success(
        val resume: Resume,
        val exactMatchQuery: String? = null,
        val semanticMatchText: String? = null
    ) : ResumeDetailsUiState()
    data class Error(val message: String) : ResumeDetailsUiState()
}

@HiltViewModel
class ResumeDetailsViewModel @Inject constructor(
    private val resumeRepository: ResumeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResumeDetailsUiState>(ResumeDetailsUiState.Loading)
    val uiState: StateFlow<ResumeDetailsUiState> = _uiState.asStateFlow()

    private val resumeId: Long = checkNotNull(savedStateHandle["resumeId"])
    private val query: String? = savedStateHandle["query"]
    private val segmentId: String? = savedStateHandle["segmentId"]

    init {
        loadResume()
    }

    private fun loadResume() {
        viewModelScope.launch {
            try {
                _uiState.value = ResumeDetailsUiState.Loading
                val resume = resumeRepository.getResume(resumeId)
                
                if (resume != null) {
                    var semanticMatchText: String? = null
                    
                    // Fetch semantic match text if segmentId is provided
                    if (segmentId != null) {
                        // We need to query embeddings to find the specific segment text
                        // Since we don't have a direct method to get embedding by segmentId in repository,
                        // we can filter the embeddings for this resume.
                        val embeddings = resumeRepository.getEmbeddingsForResume(resumeId)
                        val matchedEmbedding = embeddings.find { it.segmentId == segmentId }
                        semanticMatchText = matchedEmbedding?.segmentText
                    }
                    
                    _uiState.value = ResumeDetailsUiState.Success(
                        resume = resume,
                        exactMatchQuery = query,
                        semanticMatchText = semanticMatchText
                    )
                } else {
                    _uiState.value = ResumeDetailsUiState.Error("Resume not found")
                }
            } catch (e: Exception) {
                _uiState.value = ResumeDetailsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
