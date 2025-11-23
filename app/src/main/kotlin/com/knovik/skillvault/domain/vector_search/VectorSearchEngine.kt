package com.knovik.skillvault.domain.vector_search

import com.knovik.skillvault.data.entity.ResumeEmbedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Data class representing a search result with relevance score.
 */
data class SearchResult(
    val embedding: ResumeEmbedding,
    val similarityScore: Float, // 0 to 1, where 1 is exact match
    val resumeId: Long,
    val segmentType: String,
    val segmentText: String,
)

/**
 * Vector similarity search engine for semantic retrieval.
 * Implements cosine similarity for efficient on-device vector search.
 * 
 * This is the core "Memory" component for SkillVault.
 */
@Singleton
class VectorSearchEngine @Inject constructor() {

    /**
     * Perform semantic search against a collection of embeddings.
     *
     * @param queryEmbedding The query vector (should be same dimension as stored vectors)
     * @param candidates All candidate embeddings to search through
     * @param topK Number of top results to return
     * @param similarityThreshold Minimum similarity score to include results
     *
     * @return List of top K similar embeddings ranked by similarity score
     */
    suspend fun search(
        queryEmbedding: FloatArray,
        candidates: List<ResumeEmbedding>,
        topK: Int = 10,
        similarityThreshold: Float = 0.3f,
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        if (candidates.isEmpty()) {
            Timber.w("No candidates available for vector search")
            return@withContext emptyList()
        }

        // Validate embedding dimensions
        if (queryEmbedding.size != candidates.first().embedding.size) {
            Timber.e("Embedding dimension mismatch: query=${queryEmbedding.size}, candidate=${candidates.first().embedding.size}")
            return@withContext emptyList()
        }

        // Calculate similarity scores for all candidates
        val results = candidates.mapNotNull { embedding ->
            val similarity = cosineSimilarity(queryEmbedding, embedding.embedding)
            if (similarity >= similarityThreshold) {
                SearchResult(
                    embedding = embedding,
                    similarityScore = similarity,
                    resumeId = embedding.resumeId,
                    segmentType = embedding.segmentType,
                    segmentText = embedding.segmentText
                )
            } else {
                null
            }
        }
            .sortedByDescending { it.similarityScore }
            .take(topK)

        val endTime = System.currentTimeMillis()
        val executionTimeMs = endTime - startTime
        
        Timber.d("Vector search completed in ${executionTimeMs}ms, found ${results.size} results from ${candidates.size} candidates")

        // Log performance metrics for benchmarking
        if (executionTimeMs > 200) {
            Timber.w("Search exceeded 200ms target: ${executionTimeMs}ms")
        }

        results
    }

    /**
     * Batch search multiple queries against the same candidate set.
     * More efficient than individual searches when dealing with multiple queries.
     */
    suspend fun batchSearch(
        queryEmbeddings: List<FloatArray>,
        candidates: List<ResumeEmbedding>,
        topK: Int = 10,
        similarityThreshold: Float = 0.3f,
    ): List<List<SearchResult>> = withContext(Dispatchers.Default) {
        queryEmbeddings.map { query ->
            search(query, candidates, topK, similarityThreshold)
        }
    }

    /**
     * Calculate cosine similarity between two vectors.
     * 
     * Formula: similarity = (A · B) / (||A|| * ||B||)
     * 
     * @param vectorA First vector
     * @param vectorB Second vector
     * @return Similarity score between -1 and 1 (typically 0 to 1 for normalized embeddings)
     */
    private fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size) {
            return 0f
        }

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in vectorA.indices) {
            val a = vectorA[i].toDouble()
            val b = vectorB[i].toDouble()
            
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0f
        }

        val similarity = dotProduct / (sqrt(normA) * sqrt(normB))
        return similarity.coerceIn(-1.0, 1.0).toFloat()
    }

    /**
     * Calculate L2 (Euclidean) distance between two vectors.
     * Alternative similarity metric for comparison.
     */
    fun euclideanDistance(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size) {
            return Float.MAX_VALUE
        }

        var sumSquares = 0.0
        for (i in vectorA.indices) {
            val diff = vectorA[i] - vectorB[i]
            sumSquares += diff * diff
        }
        return sqrt(sumSquares).toFloat()
    }

    /**
     * Find the most similar embedding to the query from a list of candidates.
     * Single nearest neighbor search.
     */
    suspend fun findMostSimilar(
        queryEmbedding: FloatArray,
        candidates: List<ResumeEmbedding>,
    ): SearchResult? = withContext(Dispatchers.Default) {
        search(queryEmbedding, candidates, topK = 1).firstOrNull()
    }

    /**
     * Filter embeddings by segment type before search.
     * Useful for domain-specific searches (e.g., only search experience section).
     */
    suspend fun searchBySegmentType(
        queryEmbedding: FloatArray,
        candidates: List<ResumeEmbedding>,
        segmentType: String,
        topK: Int = 10,
        similarityThreshold: Float = 0.3f,
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val filtered = candidates.filter { it.segmentType == segmentType }
        search(queryEmbedding, filtered, topK, similarityThreshold)
    }

    /**
     * Calculate query metrics for benchmarking.
     */
    data class QueryMetrics(
        val executionTimeMs: Long,
        val candidateCount: Int,
        val resultCount: Int,
        val averageSimilarityScore: Float,
        val topScore: Float,
    )

    /**
     * Perform search with detailed metrics.
     */
    suspend fun searchWithMetrics(
        queryEmbedding: FloatArray,
        candidates: List<ResumeEmbedding>,
        topK: Int = 10,
        similarityThreshold: Float = 0.3f,
    ): Pair<List<SearchResult>, QueryMetrics> = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val results = search(queryEmbedding, candidates, topK, similarityThreshold)
        val endTime = System.currentTimeMillis()

        val metrics = QueryMetrics(
            executionTimeMs = endTime - startTime,
            candidateCount = candidates.size,
            resultCount = results.size,
            averageSimilarityScore = if (results.isNotEmpty()) results.map { it.similarityScore }.average().toFloat() else 0f,
            topScore = results.firstOrNull()?.similarityScore ?: 0f,
        )

        Pair(results, metrics)
    }
}
