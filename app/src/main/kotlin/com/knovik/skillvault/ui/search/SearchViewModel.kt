package com.knovik.skillvault.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knovik.skillvault.data.entity.SearchQuery
import com.knovik.skillvault.data.repository.ResumeRepository
import com.knovik.skillvault.domain.embedding.MediaPipeEmbeddingProvider
import com.knovik.skillvault.domain.vector_search.SearchResult
import com.knovik.skillvault.domain.vector_search.VectorSearchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for search results.
 */
sealed class SearchUIState {
    data object Idle : SearchUIState()
    data object Loading : SearchUIState()
    data class Success(val results: List<SearchResultUi>) : SearchUIState()
    data class Error(val message: String) : SearchUIState()
}

/**
 * UI model for search result with resume details.
 */
data class SearchResultUi(
    val searchResult: SearchResult,
    val resume: com.knovik.skillvault.data.entity.Resume?
)

/**
 * ViewModel for semantic search screen.
 * Handles query embedding and vector similarity search.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val resumeRepository: ResumeRepository,
    private val embeddingProvider: MediaPipeEmbeddingProvider,
    private val vectorSearchEngine: VectorSearchEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUIState>(SearchUIState.Idle)
    val uiState: StateFlow<SearchUIState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _executionTimeMs = MutableStateFlow(0L)
    val executionTimeMs: StateFlow<Long> = _executionTimeMs.asStateFlow()

    private val _resultCount = MutableStateFlow(0)
    val resultCount: StateFlow<Int> = _resultCount.asStateFlow()

    init {
        initializeEmbeddingProvider()
    }

    /**
     * Initialize MediaPipe embedder.
     */
    private fun initializeEmbeddingProvider() {
        viewModelScope.launch {
            try {
                val result = embeddingProvider.initialize()
                if (result.isSuccess) {
                    Timber.d("Embedding provider initialized")
                } else {
                    Timber.e(result.exceptionOrNull(), "Failed to initialize embedding provider")
                    _uiState.value = SearchUIState.Error("Failed to initialize embedding engine")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception during embedding provider initialization")
            }
        }
    }

    /**
     * Perform semantic search.
     *
     * @param query The search query text
     * @param topK Number of top results to return
     * @param segmentFilter Optional filter by segment type (e.g., "experience")
     */
    fun search(
        query: String,
        topK: Int = 10,
        segmentFilter: String? = null,
    ) {
        if (query.isBlank()) {
            _uiState.value = SearchUIState.Idle
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = SearchUIState.Loading
                _searchQuery.value = query
                
                val startTime = System.currentTimeMillis()

                // Generate embedding for the query
                val embeddingResult = embeddingProvider.embedText(query)
                if (embeddingResult.isFailure) {
                    _uiState.value = SearchUIState.Error("Failed to embed query")
                    return@launch
                }

                val queryEmbedding = embeddingResult.getOrThrow()

                // Get all embeddings from repository
                val candidates = resumeRepository.getAllEmbeddings()
                if (candidates.isEmpty()) {
                    _uiState.value = SearchUIState.Error("No embeddings available. Please import resumes first.")
                    return@launch
                }

                // Perform vector search
                val results = if (segmentFilter != null) {
                    vectorSearchEngine.searchBySegmentType(
                        queryEmbedding,
                        candidates,
                        segmentFilter,
                        topK
                    )
                } else {
                    vectorSearchEngine.search(queryEmbedding, candidates, topK)
                }

                // Fetch resume details for each result
                val resultsWithResume = results.map { result ->
                    val resume = resumeRepository.getResume(result.resumeId)
                    SearchResultUi(result, resume)
                }

                val endTime = System.currentTimeMillis()
                val executionTime = endTime - startTime

                // Record search query for analytics
                val searchQueryEntity = SearchQuery(
                    queryText = query,
                    queryEmbedding = queryEmbedding,
                    executionTimeMs = executionTime,
                    resultCount = results.size,
                    topScoreValue = results.firstOrNull()?.similarityScore ?: 0f,
                )
                resumeRepository.recordSearchQuery(searchQueryEntity)

                // Log performance metrics
                Timber.d("Search completed in ${executionTime}ms, found ${results.size} results from ${candidates.size} candidates")
                
                // Benchmark logging
                val resumeCount = candidates.map { it.resumeId }.distinct().size
                android.util.Log.d("EdgeScoutExperiment", "BENCHMARK_DATA, $resumeCount, \"$query\", $executionTime")

                if (executionTime > 200) {
                    Timber.w("Search execution time exceeded 200ms target: ${executionTime}ms")
                }

                _uiState.value = SearchUIState.Success(resultsWithResume)
                _executionTimeMs.value = executionTime
                _resultCount.value = results.size

            } catch (e: Exception) {
                Timber.e(e, "Search failed")
                _uiState.value = SearchUIState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Clear search results.
     */
    fun clearSearch() {
        _uiState.value = SearchUIState.Idle
        _searchQuery.value = ""
        _executionTimeMs.value = 0
        _resultCount.value = 0
    }

    /**
     * Record user feedback for a search result.
     */
    fun recordSearchFeedback(satisfied: Boolean, feedbackText: String = "") {
        viewModelScope.launch {
            try {
                // Update last search query with feedback
                val history = resumeRepository.getSearchHistory(1)
                if (history.isNotEmpty()) {
                    val lastQuery = history.first()
                    lastQuery.wasUserSatisfied = satisfied
                    lastQuery.feedbackText = feedbackText
                    resumeRepository.recordSearchQuery(lastQuery)
                    Timber.d("Search feedback recorded")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to record feedback")
            }
        }
    }

    /**
     * Regenerate all embeddings.
     * Useful when the embedding model or logic changes.
     */
    fun regenerateAllEmbeddings() {
        viewModelScope.launch {
            try {
                _uiState.value = SearchUIState.Loading
                
                // 1. Clear all existing embeddings
                val allResumes = resumeRepository.getAllResumes(limit = 10000)
                allResumes.forEach { resume ->
                    resumeRepository.deleteEmbeddingsForResume(resume.id)
                    resume.isEmbedded = false
                    resume.processingStatus = "pending"
                    resumeRepository.insertOrUpdateResume(resume)
                }
                
                // 2. Trigger ingestion worker
                // We need to inject WorkManager or use a repository method that triggers it.
                // For now, we'll assume the user will go to Import screen or we can trigger it if we had the worker.
                // But since we don't have WorkManager injected here, we'll just reset the status so the worker picks it up next time.
                
                _uiState.value = SearchUIState.Idle
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to regenerate embeddings")
                _uiState.value = SearchUIState.Error("Failed to reset embeddings")
            }
        }
    }
}
