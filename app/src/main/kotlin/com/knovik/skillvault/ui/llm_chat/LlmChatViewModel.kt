package com.knovik.skillvault.ui.llm_chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knovik.skillvault.data.entity.Resume
import com.knovik.skillvault.data.repository.ResumeRepository
import com.knovik.skillvault.domain.llm.LlmInferenceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class ChatUiState {
    data object Idle : ChatUiState()
    data object Loading : ChatUiState()
    data class Success(val response: String) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
    data object ModelMissing : ChatUiState()
}

@HiltViewModel
class LlmChatViewModel @Inject constructor(
    private val llmProvider: LlmInferenceProvider,
    private val resumeRepository: ResumeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun checkModelAvailability() {
        if (!llmProvider.isModelAvailable()) {
            _uiState.value = ChatUiState.ModelMissing
        } else {
            _uiState.value = ChatUiState.Idle
        }
    }

    fun analyzeCandidateFit(resumeId: Long, roleDescription: String) {
        viewModelScope.launch {
            try {
                _uiState.value = ChatUiState.Loading
                
                val resume = resumeRepository.getResume(resumeId)
                if (resume == null) {
                    _uiState.value = ChatUiState.Error("Resume not found")
                    return@launch
                }

                if (!llmProvider.isModelAvailable()) {
                    _uiState.value = ChatUiState.ModelMissing
                    return@launch
                }

                val prompt = buildPrompt(resume, roleDescription)
                
                val startTime = System.currentTimeMillis()
                val response = llmProvider.generateResponse(prompt)
                val endTime = System.currentTimeMillis()
                
                val durationSeconds = (endTime - startTime) / 1000.0
                android.util.Log.d("EdgeScoutGen", "BENCHMARK_GEN: Analysis took $durationSeconds seconds")
                
                _uiState.value = ChatUiState.Success(response)

            } catch (e: Throwable) {
                Timber.e(e, "LLM Analysis failed")
                _uiState.value = ChatUiState.Error("Analysis failed: ${e.message ?: "Unknown error"}. Ensure device has enough RAM.")
            }
        }
    }

    private fun buildPrompt(resume: Resume, roleDescription: String): String {
        // Slightly expanded prompt to get better results while keeping it safe.
        // Explicitly asking for "Evaluation" to prevent JD generation.
        
        val safeRole = roleDescription.take(200)
        val safeSummary = resume.summary.take(300)
        val safeSkills = resume.skills.take(200)
        
        return """
            <start_of_turn>user
            You are an HR Assistant. Evaluate if this candidate is a good fit for the role.
            
            ROLE:
            $safeRole
            
            CANDIDATE:
            Summary: $safeSummary
            Skills: $safeSkills
            
            QUESTION: Is this candidate a good fit? Answer with Yes/No and a brief reason.
            <end_of_turn>
            <start_of_turn>model
        """.trimIndent()
    }
}
