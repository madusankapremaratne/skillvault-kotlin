package com.knovik.skillvault.data.repository

import com.knovik.skillvault.data.entity.Resume
import com.knovik.skillvault.data.entity.ResumeEmbedding
import com.knovik.skillvault.data.entity.SearchQuery
import com.knovik.skillvault.data.entity.PerformanceMetric
import com.knovik.skillvault.data.entity.Resume_
import com.knovik.skillvault.data.entity.ResumeEmbedding_
import com.knovik.skillvault.data.entity.SearchQuery_
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.query.Query
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Resume data operations using ObjectBox.
 * Implements the repository pattern for clean architecture separation.
 */
@Singleton
class ResumeRepository @Inject constructor(
    private val resumeBox: Box<Resume>,
    private val embeddingBox: Box<ResumeEmbedding>,
    private val searchQueryBox: Box<SearchQuery>,
    private val performanceMetricBox: Box<PerformanceMetric>,
) {

    /**
     * Insert a new resume or update existing one.
     */
    suspend fun insertOrUpdateResume(resume: Resume): Long = withContext(Dispatchers.IO) {
        resume.updatedAt = System.currentTimeMillis()
        val id = resumeBox.put(resume)
        Timber.d("Resume inserted/updated with ID: $id")
        id
    }

    /**
     * Retrieve a resume by ID.
     */
    suspend fun getResume(id: Long): Resume? = withContext(Dispatchers.IO) {
        resumeBox.get(id)
    }

    /**
     * Retrieve a resume by resumeId (string identifier).
     */
    suspend fun getResumeByExternalId(resumeId: String): Resume? = withContext(Dispatchers.IO) {
        resumeBox.query()
            .equal(Resume_.resumeId, resumeId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .findFirst()
    }

    /**
     * Get all resumes with pagination support.
     */
    suspend fun getAllResumes(
        offset: Int = 0,
        limit: Int = 50,
        sortByNewest: Boolean = true
    ): List<Resume> = withContext(Dispatchers.IO) {
        var queryBuilder = resumeBox.query()
        if (sortByNewest) {
            queryBuilder = queryBuilder.order(Resume_.createdAt, QueryBuilder.DESCENDING)
        }
        queryBuilder.build().find(offset.toLong(), limit.toLong())
    }

    /**
     * Get count of total resumes.
     */
    suspend fun getResumeCount(): Long = withContext(Dispatchers.IO) {
        resumeBox.count()
    }

    /**
     * Get resumes by processing status (e.g., "pending", "completed", "failed").
     */
    suspend fun getResumesByStatus(status: String): List<Resume> = withContext(Dispatchers.IO) {
        resumeBox.query()
            .equal(Resume_.processingStatus, status, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .find()
    }

    /**
     * Get unembedded resumes for batch processing.
     */
    suspend fun getUnembeddedResumes(limit: Int = 100): List<Resume> = withContext(Dispatchers.IO) {
        resumeBox.query()
            .equal(Resume_.isEmbedded, false)
            .order(Resume_.createdAt)
            .build()
            .find(0, limit.toLong())
    }

    /**
     * Update resume embedding status.
     */
    suspend fun markResumeAsEmbedded(resumeId: Long, success: Boolean = true) = withContext(Dispatchers.IO) {
        val resume = resumeBox.get(resumeId) ?: return@withContext
        resume.isEmbedded = success
        resume.embeddedAt = System.currentTimeMillis()
        resume.processingStatus = if (success) "completed" else "failed"
        resume.updatedAt = System.currentTimeMillis()
        resumeBox.put(resume)
        Timber.d("Resume $resumeId marked as embedded: $success")
    }

    /**
     * Delete a resume and its associated embeddings.
     */
    suspend fun deleteResume(resumeId: Long) = withContext(Dispatchers.IO) {
        // Delete associated embeddings first
        val embeddings = embeddingBox.query()
            .equal(ResumeEmbedding_.resumeId, resumeId)
            .build()
            .find()
        embeddingBox.remove(embeddings)
        
        // Delete resume
        resumeBox.remove(resumeId)
        Timber.d("Resume $resumeId and its embeddings deleted")
    }

    /**
     * Insert embedding for a resume segment.
     */
    suspend fun insertEmbedding(embedding: ResumeEmbedding): Long = withContext(Dispatchers.IO) {
        val id = embeddingBox.put(embedding)
        Timber.d("Embedding inserted with ID: $id")
        id
    }

    /**
     * Batch insert embeddings for better performance.
     */
    suspend fun insertEmbeddingsBatch(embeddings: List<ResumeEmbedding>) = withContext(Dispatchers.IO) {
        embeddingBox.put(embeddings)
        Timber.d("Batch inserted ${embeddings.size} embeddings")
    }

    /**
     * Get embeddings for a specific resume.
     */
    suspend fun getEmbeddingsForResume(resumeId: Long): List<ResumeEmbedding> = withContext(Dispatchers.IO) {
        embeddingBox.query()
            .equal(ResumeEmbedding_.resumeId, resumeId)
            .build()
            .find()
    }

    /**
     * Get embeddings by segment type (e.g., "experience", "skills").
     */
    suspend fun getEmbeddingsBySegmentType(segmentType: String): List<ResumeEmbedding> = withContext(Dispatchers.IO) {
        embeddingBox.query()
            .equal(ResumeEmbedding_.segmentType, segmentType, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .find()
    }

    /**
     * Get all embeddings (for vector similarity search).
     */
    suspend fun getAllEmbeddings(limit: Int = 10000): List<ResumeEmbedding> = withContext(Dispatchers.IO) {
        embeddingBox.query()
            .order(ResumeEmbedding_.createdAt, QueryBuilder.DESCENDING)
            .build()
            .find(0, limit.toLong())
    }

    /**
     * Get embedding count for statistics.
     */
    suspend fun getEmbeddingCount(): Long = withContext(Dispatchers.IO) {
        embeddingBox.count()
    }

    /**
     * Delete all embeddings for a resume.
     */
    suspend fun deleteEmbeddingsForResume(resumeId: Long) = withContext(Dispatchers.IO) {
        val embeddings = embeddingBox.query()
            .equal(ResumeEmbedding_.resumeId, resumeId)
            .build()
            .find()
        embeddingBox.remove(embeddings)
    }

    /**
     * Search resumes by text using FTS (Full-Text Search) for keyword matching.
     * This is complementary to semantic vector search.
     */
    suspend fun searchResumesByKeyword(keyword: String): List<Resume> = withContext(Dispatchers.IO) {
        resumeBox.query()
            .apply {
                // Search across multiple fields
                val keywordLower = keyword.lowercase()
                filter { resume ->
                    resume.fullName.lowercase().contains(keywordLower) ||
                    resume.skills.lowercase().contains(keywordLower) ||
                    resume.experience.lowercase().contains(keywordLower) ||
                    resume.summary.lowercase().contains(keywordLower)
                }
            }
            .build()
            .find()
    }

    /**
     * Get storage statistics for benchmarking.
     */
    suspend fun getStorageStats(): Map<String, Long> = withContext(Dispatchers.IO) {
        val resumeCount = resumeBox.count()
        val embeddingCount = embeddingBox.count()
        
        // Estimate storage size (rough calculation)
        val allResumes = resumeBox.all
        val totalTextSize = allResumes.sumOf { it.rawText.length.toLong() }
        
        // Each embedding: 384 floats * 4 bytes = 1536 bytes
        val embeddingStorageSize = embeddingCount * 1536
        
        mapOf(
            "resumeCount" to resumeCount,
            "embeddingCount" to embeddingCount,
            "totalTextSizeBytes" to totalTextSize,
            "embeddingStorageSizeBytes" to embeddingStorageSize,
            "estimatedTotalSizeBytes" to (totalTextSize + embeddingStorageSize)
        )
    }

    /**
     * Clear all data (use with caution).
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        embeddingBox.removeAll()
        resumeBox.removeAll()
        searchQueryBox.removeAll()
        Timber.w("All data cleared from database")
    }

    /**
     * Record a search query for analytics.
     */
    suspend fun recordSearchQuery(query: SearchQuery) = withContext(Dispatchers.IO) {
        searchQueryBox.put(query)
    }

    /**
     * Get search history.
     */
    suspend fun getSearchHistory(limit: Int = 50): List<SearchQuery> = withContext(Dispatchers.IO) {
        searchQueryBox.query()
            .order(SearchQuery_.executedAt, QueryBuilder.DESCENDING)
            .build()
            .find(0, limit.toLong())
    }

    /**
     * Record a performance metric.
     */
    suspend fun recordMetric(metric: PerformanceMetric) = withContext(Dispatchers.IO) {
        performanceMetricBox.put(metric)
    }
}
